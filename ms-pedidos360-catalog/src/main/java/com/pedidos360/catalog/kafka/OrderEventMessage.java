package com.pedidos360.catalog.kafka;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Mensaje del tópico {@link KafkaTopics#ORDER_EVENTS} publicado por orders (clave = orderId).
 * Debe mantenerse compatible con com.pedidos360.orders.messaging.kafka.OrderEventMessage.
 */
public record OrderEventMessage(
        @NotNull UUID eventId,
        @NotNull OrderEventType type,
        @NotNull @Positive Long orderId,
        @NotBlank String status,
        String previousStatus,
        String customerEmail,
        String customerName,
        BigDecimal total,
        List<@Valid Item> items,
        @NotNull Instant occurredAt) {

    public record Item(@NotNull Long productId, String productName, @NotNull @Positive Integer quantity,
                       BigDecimal unitPrice, BigDecimal subtotal) {
    }
}
