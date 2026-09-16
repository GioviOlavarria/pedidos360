package com.pedidos360.orders.domain.model.service;

import com.pedidos360.orders.domain.model.Order;
import com.pedidos360.orders.domain.model.OrderStatus;
import com.pedidos360.orders.domain.model.exception.InvalidOrderStateException;
import com.pedidos360.orders.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    public Order createOrder(com.pedidos360.orders.api.dto.OrderRequest request) {
        Order order = new Order();
        order.setCustomerId(request.getCustomerId());
        order.setStatus(OrderStatus.CREADO);
        order.setCreatedAt(LocalDateTime.now());
        order.setTotal(request.getTotal());
        
        if (request.getItems() != null) {
            for (com.pedidos360.orders.api.dto.OrderItemRequest itemReq : request.getItems()) {
                com.pedidos360.orders.domain.model.OrderItem item = new com.pedidos360.orders.domain.model.OrderItem(
                    itemReq.getProductId(), itemReq.getQuantity(), itemReq.getUnitPrice());
                order.addItem(item);
            }
        }
        
        return orderRepository.save(order);
    }

    public Order updateOrder(Long id, com.pedidos360.orders.api.dto.OrderRequest request) {
        Order order = findById(id);
        order.setCustomerId(request.getCustomerId());
        order.setTotal(request.getTotal());
        
        order.getItems().clear();
        
        if (request.getItems() != null) {
            for (com.pedidos360.orders.api.dto.OrderItemRequest itemReq : request.getItems()) {
                com.pedidos360.orders.domain.model.OrderItem item = new com.pedidos360.orders.domain.model.OrderItem(
                    itemReq.getProductId(), itemReq.getQuantity(), itemReq.getUnitPrice());
                order.addItem(item);
            }
        }
        
        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public List<Order> findAll() {
        return orderRepository.findAll();
    }

    public void deleteOrder(Long id) {
        if (!orderRepository.existsById(id)) {
            throw new IllegalArgumentException("Order no encontrada: " + id);
        }
        orderRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Order findById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Order no encontrada: " + id));
    }

    public Order changeStatus(Long orderId, OrderStatus nuevoEstado) {
        Order order = findById(orderId);
        OrderStatus estadoActual = order.getStatus();

        validateStateTransition(estadoActual, nuevoEstado);

        order.setStatus(nuevoEstado);
        return orderRepository.save(order);
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