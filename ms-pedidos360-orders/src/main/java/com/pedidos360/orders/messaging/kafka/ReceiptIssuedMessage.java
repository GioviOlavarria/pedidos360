package com.pedidos360.orders.messaging.kafka;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Mensaje del tópico {@link KafkaTopics#BILLING_RECEIPTS} publicado por notifications cuando emite la boleta
 * electrónica (SII) y la envía al cliente. Debe mantenerse compatible con la copia de notifications.
 */
public record ReceiptIssuedMessage(
        @NotNull @Positive Long orderId,
        @NotNull Integer documentType,
        @NotNull @Positive Long folio,
        @NotBlank String trackId,
        @NotNull @PositiveOrZero BigDecimal netAmount,
        @NotNull @PositiveOrZero BigDecimal taxAmount,
        @NotNull @PositiveOrZero BigDecimal total,
        @NotBlank String siiStatus,
        String emailedTo,
        @NotNull Instant issuedAt) {
}
