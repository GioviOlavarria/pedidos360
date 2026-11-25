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
import com.pedidos360.orders.messaging.OrderChangedEvent;
import com.pedidos360.orders.messaging.OrderPaidEvent;
import com.pedidos360.orders.messaging.kafka.OrderEventType;
import com.pedidos360.orders.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
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
            EnumSet.of(OrderStatus.PENDIENTE_PAGO, OrderStatus.CREADO, OrderStatus.ACEPTADO, OrderStatus.EN_PREPARACION);

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
     * Crea un pedido PENDIENTE_PAGO: reserva stock en el catálogo (que entrega los precios vigentes) y calcula el
     * total en el servidor. La boleta se emite recién cuando se confirma el pago (ver {@link #markPaid}).
     */
    public Order createOrder(OrderRequest request, CurrentUser user) {
        List<StockItem> stockItems = toStockItems(request.getItems());
        List<ReservedItem> reserved = catalogClient.reserve(stockItems);

        Order order = new Order();
        order.setCustomerId(request.getCustomerId());
        order.setCustomerEmail(user.email());
        order.setCustomerName(user.name());
        order.setStatus(OrderStatus.PENDIENTE_PAGO);
        order.setCreatedAt(LocalDateTime.now());
        applyItems(order, reserved);

        Order saved;
        try {
            saved = orderRepository.saveAndFlush(order);
        } catch (RuntimeException e) {
            releaseQuietly(stockItems);
            throw e;
        }

        eventPublisher.publishEvent(OrderChangedEvent.of(OrderEventType.CREATED, saved, null));
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
        Order saved;
        try {
            saved = orderRepository.saveAndFlush(order);
        } catch (RuntimeException e) {
            releaseQuietly(requested);
            reserveQuietly(previous);
            throw e;
        }
        eventPublisher.publishEvent(OrderChangedEvent.of(OrderEventType.UPDATED, order, order.getStatus()));
        return saved;
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

    /**
     * Seguimiento de un pedido: Admin y Operator ven cualquiera; el cliente solo los suyos. Un pedido ajeno responde
     * igual que uno inexistente (404) para no revelar qué números de pedido existen.
     */
    @Transactional(readOnly = true)
    public Order findVisibleTo(Long id, CurrentUser user) {
        Order order = findById(id);
        if (user.canManageAllOrders()) {
            return order;
        }
        if (user.email() == null || !user.email().equalsIgnoreCase(order.getCustomerEmail())) {
            throw new OrderNotFoundException(id);
        }
        return order;
    }

    public void deleteOrder(Long id) {
        Order order = findById(id);
        if (STOCK_IN_WAREHOUSE.contains(order.getStatus())) {
            catalogClient.release(itemsOf(order));
        }
        OrderChangedEvent deleted = OrderChangedEvent.of(OrderEventType.DELETED, order, order.getStatus());
        orderRepository.delete(order);
        eventPublisher.publishEvent(deleted);
    }

    public Order changeStatus(Long orderId, OrderStatus nuevoEstado) {
        Order order = findById(orderId);
        validateStateTransition(order.getStatus(), nuevoEstado);
        if (order.getStatus() == OrderStatus.PENDIENTE_PAGO && nuevoEstado == OrderStatus.CREADO) {
            // El Admin registra un pago recibido fuera de la pasarela (p. ej. transferencia).
            return markPaid(orderId, "MANUAL-ADMIN", Instant.now());
        }

        if (nuevoEstado == OrderStatus.CANCELADO) {
            catalogClient.release(itemsOf(order));
        }

        OrderStatus anterior = order.getStatus();
        order.setStatus(nuevoEstado);
        Order saved = orderRepository.save(order);
        eventPublisher.publishEvent(OrderChangedEvent.of(OrderEventType.STATUS_CHANGED, order, anterior));
        return saved;
    }

    /**
     * Pago confirmado: PENDIENTE_PAGO → CREADO y se programa la boleta (RabbitMQ) para después del commit.
     * Es idempotente: si el pedido ya registró ese mismo pago no hace nada. Un pago que llega para un pedido que
     * ya no está pendiente (p. ej. vencido) se deja en el log para devolución manual.
     */
    public Order markPaid(Long orderId, String paymentReference, Instant paidAt) {
        Order order = findById(orderId);
        if (order.getStatus() != OrderStatus.PENDIENTE_PAGO) {
            if (!paymentReference.equals(order.getPaymentReference())) {
                log.error("Pago {} recibido para el pedido #{} en estado {}: requiere devolución manual",
                        paymentReference, orderId, order.getStatus());
            }
            return order;
        }
        order.setStatus(OrderStatus.CREADO);
        order.setPaidAt(paidAt);
        order.setPaymentReference(paymentReference);
        Order saved = orderRepository.save(order);
        eventPublisher.publishEvent(OrderChangedEvent.of(OrderEventType.STATUS_CHANGED, order, OrderStatus.PENDIENTE_PAGO));
        eventPublisher.publishEvent(toEvent(order));
        log.info("Pedido #{} pagado ({}): se emitirá la boleta", orderId, paymentReference);
        return saved;
    }

    /** Pago rechazado, anulado o vencido: el pedido pendiente se cancela y se devuelve el stock. */
    public void cancelUnpaid(Long orderId, String reason) {
        Order order = findById(orderId);
        if (order.getStatus() != OrderStatus.PENDIENTE_PAGO) {
            return;
        }
        releaseQuietly(itemsOf(order));
        order.setStatus(OrderStatus.CANCELADO);
        orderRepository.save(order);
        eventPublisher.publishEvent(OrderChangedEvent.of(OrderEventType.STATUS_CHANGED, order, OrderStatus.PENDIENTE_PAGO));
        log.info("Pedido #{} cancelado: {}", orderId, reason);
    }

    /**
     * Aplica el resultado de un pago (evento Kafka de ms-pedidos360-payments). El monto pagado debe cubrir el
     * total del pedido; si no, el evento es inválido (IllegalArgumentException → DLT sin reintentos).
     */
    public void applyPaymentResult(Long orderId, String status, BigDecimal amount, String paymentReference, Instant at) {
        if (orderRepository.findById(orderId).isEmpty()) {
            log.warn("Resultado de pago {} para pedido #{} inexistente: se descarta", paymentReference, orderId);
            return;
        }
        switch (status) {
            case "PAID" -> {
                BigDecimal total = findById(orderId).getTotal();
                if (amount == null || amount.compareTo(total.setScale(0, RoundingMode.HALF_UP)) < 0) {
                    throw new IllegalArgumentException("Pago " + paymentReference + " por " + amount
                            + " no cubre el total del pedido #" + orderId + " (" + total + ")");
                }
                markPaid(orderId, paymentReference, at);
            }
            case "REJECTED", "CANCELLED", "EXPIRED" -> cancelUnpaid(orderId, "pago " + paymentReference + " " + status);
            default -> throw new IllegalArgumentException("Estado de pago desconocido: " + status);
        }
    }

    /** Cancela los pedidos que siguen sin pagar después del plazo (libera el stock reservado). */
    public int expireUnpaid(Duration maxAge) {
        LocalDateTime cutoff = LocalDateTime.now().minus(maxAge);
        List<Order> expired = orderRepository.findByStatusAndCreatedAtBefore(OrderStatus.PENDIENTE_PAGO, cutoff);
        expired.forEach(order -> cancelUnpaid(order.getId(), "pago no completado en " + maxAge.toMinutes() + " minutos"));
        return expired.size();
    }

    /**
     * Registra la boleta electrónica emitida para un pedido (evento Kafka de notifications).
     * Es idempotente: reprocesar el mismo mensaje deja el pedido igual. Si el pedido ya no existe
     * (fue eliminado) no hay nada que actualizar y el evento se descarta.
     */
    public void registerReceipt(Long orderId, Long folio, String trackId, String siiStatus, Instant issuedAt) {
        orderRepository.findById(orderId).ifPresentOrElse(order -> {
            order.setReceiptFolio(folio);
            order.setReceiptTrackId(trackId);
            order.setReceiptSiiStatus(siiStatus);
            order.setReceiptIssuedAt(issuedAt);
            orderRepository.save(order);
            log.info("Pedido #{}: boleta folio {} registrada (SII {}, track {})", orderId, folio, siiStatus, trackId);
        }, () -> log.warn("Boleta folio {} para pedido #{} inexistente: se descarta", folio, orderId));
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

    private static OrderPaidEvent toEvent(Order order) {
        List<OrderPaidEvent.Item> items = order.getItems().stream()
                .map(i -> new OrderPaidEvent.Item(i.getProductId(), i.getProductName(), i.getQuantity(),
                        i.getUnitPrice(), i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity()))))
                .toList();
        return new OrderPaidEvent(order.getId(), order.getCustomerEmail(), order.getCustomerName(),
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
            case PENDIENTE_PAGO:
                if (nuevoEstado != OrderStatus.CREADO && nuevoEstado != OrderStatus.CANCELADO) {
                    throw new InvalidOrderStateException(
                            "Un pedido sin pagar solo puede confirmarse como pagado (CREADO) o CANCELARSE, no pasar a " + nuevoEstado);
                }
                break;

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
