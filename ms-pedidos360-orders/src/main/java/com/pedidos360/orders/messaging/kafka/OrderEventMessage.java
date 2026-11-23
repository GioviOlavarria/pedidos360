package com.pedidos360.orders.messaging.kafka;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Mensaje del tópico {@link KafkaTopics#ORDER_EVENTS}, serializado como JSON. La clave del registro es el orderId,
 * así todos los eventos de un pedido caen en la misma partición y se consumen en orden.
 * Lleva el estado completo del pedido (event-carried state transfer) para que los consumidores no
 * tengan que llamar a orders. Debe mantenerse compatible con las copias de catalog y notifications.
 */
public record OrderEventMessage(
        UUID eventId,
        OrderEventType type,
        Long orderId,
        String status,
        String previousStatus,
        String customerEmail,
        String customerName,
        BigDecimal total,
        List<Item> items,
        Instant occurredAt) {

    public record Item(Long productId, String productName, Integer quantity, BigDecimal unitPrice, BigDecimal subtotal) {
    }
}
