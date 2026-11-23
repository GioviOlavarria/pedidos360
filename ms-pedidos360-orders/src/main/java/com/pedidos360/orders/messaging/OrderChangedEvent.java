package com.pedidos360.orders.messaging;

import com.pedidos360.orders.domain.model.Order;
import com.pedidos360.orders.domain.model.OrderStatus;
import com.pedidos360.orders.messaging.kafka.OrderEventType;

import java.math.BigDecimal;
import java.util.List;

/**
 * Evento interno (Spring) que se emite al editar, cambiar de estado o eliminar un pedido.
 * Es una foto del pedido tomada dentro de la transacción; se publica en Kafka después del commit.
 * La creación usa {@link OrderPaidEvent}, que además dispara la boleta por RabbitMQ.
 */
public record OrderChangedEvent(
        OrderEventType type,
        Long orderId,
        OrderStatus status,
        OrderStatus previousStatus,
        String customerEmail,
        String customerName,
        BigDecimal total,
        List<OrderPaidEvent.Item> items) {

    public static OrderChangedEvent of(OrderEventType type, Order order, OrderStatus previousStatus) {
        List<OrderPaidEvent.Item> items = order.getItems().stream()
                .map(i -> new OrderPaidEvent.Item(i.getProductId(), i.getProductName(), i.getQuantity(),
                        i.getUnitPrice(), i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity()))))
                .toList();
        return new OrderChangedEvent(type, order.getId(), order.getStatus(), previousStatus,
                order.getCustomerEmail(), order.getCustomerName(), order.getTotal(), items);
    }
}
