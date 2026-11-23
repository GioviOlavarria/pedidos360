package com.pedidos360.orders.messaging.kafka;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Mensaje del tópico {@link KafkaTopics#PAYMENT_EVENTS} publicado por ms-pedidos360-payments (clave = orderId).
 * Debe mantenerse compatible con la copia de payments.
 */
public record PaymentResultMessage(
        @NotNull @Positive Long paymentId,
        @NotNull @Positive Long orderId,
        @NotBlank @Pattern(regexp = "PAID|REJECTED|CANCELLED|EXPIRED") String status,
        @NotNull @PositiveOrZero BigDecimal amount,
        @NotBlank String provider,
        String providerReference,
        String paymentMethod,
        @NotNull Instant occurredAt) {

    /** Referencia que se guarda en el pedido, p. ej. "FLOW-123456" o "SIMULADO-15". */
    public String reference() {
        return provider + "-" + (providerReference != null ? providerReference : paymentId);
    }
}
