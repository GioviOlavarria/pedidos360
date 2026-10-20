package com.pedidos360.orders.domain.model.service;

import com.pedidos360.orders.api.dto.OrderItemRequest;
import com.pedidos360.orders.api.dto.OrderRequest;
import com.pedidos360.orders.client.CatalogClient;
import com.pedidos360.orders.client.CatalogClient.ReservedItem;
import com.pedidos360.orders.client.CatalogClient.StockItem;
import com.pedidos360.orders.domain.model.Order;
import com.pedidos360.orders.domain.model.OrderItem;
import com.pedidos360.orders.domain.model.OrderStatus;
import com.pedidos360.orders.domain.model.exception.InvalidOrderStateException;
import com.pedidos360.orders.domain.model.exception.OrderNotFoundException;
import com.pedidos360.orders.messaging.OrderCreatedEvent;
import com.pedidos360.orders.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    /** Estados en que la mercadería aún está en bodega: eliminar el pedido devuelve el stock. */
    private static final Set<OrderStatus> STOCK_IN_WAREHOUSE =
            EnumSet.of(OrderStatus.CREADO, OrderStatus.ACEPTADO, OrderStatus.EN_PREPARACION);

    /** Estados en que el Admin todavía puede modificar los productos del pedido. */
    private static final Set<OrderStatus> EDITABLE = EnumSet.of(OrderStatus.CREADO, OrderStatus.ACEPTADO);

    private final OrderRepository orderRepository;
    private final CatalogClient catalogClient;
    private final ApplicationEventPublisher eventPublisher;

    public OrderService(OrderRepository orderRepository, CatalogClient catalogClient,
                        ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.catalogClient = catalogClient;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Crea un pedido: reserva stock en el catálogo (que entrega los precios vigentes), calcula el
     * total en el servidor, guarda el pedido y deja programado el evento para la boleta por correo.
     */
    public Order createOrder(OrderRequest request, CurrentUser user) {
        List<StockItem> stockItems = toStockItems(request.getItems());
        List<ReservedItem> reserved = catalogClient.reserve(stockItems);

        Order order = new Order();
        order.setCustomerId(request.getCustomerId());
        order.setCustomerEmail(user.email());
        order.setCustomerName(user.name());
        order.setStatus(OrderStatus.CREADO);
        order.setCreatedAt(LocalDateTime.now());
        applyItems(order, reserved);

        Order saved;
        try {
            saved = orderRepository.saveAndFlush(order);
        } catch (RuntimeException e) {
            releaseQuietly(stockItems);
            throw e;
        }

        eventPublisher.publishEvent(toEvent(saved));
        return saved;
    }

    public Order updateOrder(Long id, OrderRequest request) {
        Order order = findById(id);
        if (!EDITABLE.contains(order.getStatus())) {
            throw new InvalidOrderStateException(
                    "Solo se pueden editar pedidos en estado CREADO o ACEPTADO (actual: " + order.getStatus() + ")");
        }

        List<StockItem> previous = itemsOf(order);
        List<StockItem> requested = toStockItems(request.getItems());

        // Devolver primero el stock anterior para que la nueva reserva vea el stock completo.
        catalogClient.release(previous);
        List<ReservedItem> reserved;
        try {
            reserved = catalogClient.reserve(requested);
        } catch (RuntimeException e) {
            reserveQuietly(previous);
            throw e;
        }

        order.setCustomerId(request.getCustomerId());
        order.getItems().clear();
        applyItems(order, reserved);
        try {
            return orderRepository.saveAndFlush(order);
        } catch (RuntimeException e) {
            releaseQuietly(requested);
            reserveQuietly(previous);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<Order> findAllFor(CurrentUser user) {
        if (user.canManageAllOrders()) {
            return orderRepository.findAllByOrderByCreatedAtDesc();
        }
        if (user.email() == null) {
            return List.of();
        }
        return orderRepository.findByCustomerEmailIgnoreCaseOrderByCreatedAtDesc(user.email());
    }

    @Transactional(readOnly = true)
    public Order findById(Long id) {
        return orderRepository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    public void deleteOrder(Long id) {
        Order order = findById(id);
        if (STOCK_IN_WAREHOUSE.contains(order.getStatus())) {
            catalogClient.release(itemsOf(order));
        }
        orderRepository.delete(order);
    }

    public Order changeStatus(Long orderId, OrderStatus nuevoEstado) {
        Order order = findById(orderId);
        validateStateTransition(order.getStatus(), nuevoEstado);

        if (nuevoEstado == OrderStatus.CANCELADO) {
            catalogClient.release(itemsOf(order));
        }

        order.setStatus(nuevoEstado);
        return orderRepository.save(order);
    }

    private void applyItems(Order order, List<ReservedItem> reserved) {
        BigDecimal total = BigDecimal.ZERO;
        for (ReservedItem r : reserved) {
            order.addItem(new OrderItem(r.productId(), r.name(), r.quantity(), r.unitPrice()));
            total = total.add(r.unitPrice().multiply(BigDecimal.valueOf(r.quantity())));
        }
        order.setTotal(total);
    }

    private static List<StockItem> toStockItems(List<OrderItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("El pedido debe tener al menos un producto");
        }
        return items.stream().map(i -> new StockItem(i.getProductId(), i.getQuantity())).toList();
    }

    private static List<StockItem> itemsOf(Order order) {
        return order.getItems().stream().map(i -> new StockItem(i.getProductId(), i.getQuantity())).toList();
    }

    private void releaseQuietly(List<StockItem> items) {
        try {
            catalogClient.release(items);
        } catch (RuntimeException e) {
            log.error("No se pudo devolver stock {} tras un error: {}", items, e.getMessage());
        }
    }

    private void reserveQuietly(List<StockItem> items) {
        try {
            catalogClient.reserve(items);
        } catch (RuntimeException e) {
            log.error("No se pudo restaurar la reserva de stock {}: {}", items, e.getMessage());
        }
    }

    private static OrderCreatedEvent toEvent(Order order) {
        List<OrderCreatedEvent.Item> items = order.getItems().stream()
                .map(i -> new OrderCreatedEvent.Item(i.getProductId(), i.getProductName(), i.getQuantity(),
                        i.getUnitPrice(), i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity()))))
                .toList();
        return new OrderCreatedEvent(order.getId(), order.getCustomerEmail(), order.getCustomerName(),
                order.getCreatedAt(), order.getTotal(), items);
    }

    private void validateStateTransition(OrderStatus estadoActual, OrderStatus nuevoEstado) {
        if (estadoActual == OrderStatus.ENTREGADO) {
            throw new InvalidOrderStateException(
                    "No se puede cambiar el estado de un pedido ENTREGADO");
        }

        if (estadoActual == OrderStatus.CANCELADO) {
            throw new InvalidOrderStateException(
                    "No se puede cambiar el estado de un pedido CANCELADO");
        }

        switch (estadoActual) {
            case CREADO:
                if (nuevoEstado != OrderStatus.ACEPTADO && nuevoEstado != OrderStatus.CANCELADO) {
                    throw new InvalidOrderStateException(
                            "Desde CREADO solo se puede pasar a ACEPTADO o CANCELADO, no a " + nuevoEstado);
                }
                break;

            case ACEPTADO:
                if (nuevoEstado != OrderStatus.EN_PREPARACION && nuevoEstado != OrderStatus.CANCELADO) {
                    throw new InvalidOrderStateException(
                            "Desde ACEPTADO solo se puede pasar a EN_PREPARACION o CANCELADO, no a " + nuevoEstado);
                }
                break;

            case EN_PREPARACION:
                if (nuevoEstado != OrderStatus.DESPACHADO && nuevoEstado != OrderStatus.CANCELADO) {
                    throw new InvalidOrderStateException(
                            "Desde EN_PREPARACION solo se puede pasar a DESPACHADO o CANCELADO, no a " + nuevoEstado);
                }
                break;

            case DESPACHADO:
                if (nuevoEstado != OrderStatus.ENTREGADO && nuevoEstado != OrderStatus.CANCELADO) {
                    throw new InvalidOrderStateException(
                            "Desde DESPACHADO solo se puede pasar a ENTREGADO o CANCELADO, no a " + nuevoEstado);
                }
                break;

            default:
                throw new InvalidOrderStateException(
                        "Estado desconocido: " + estadoActual);
        }
    }
}
