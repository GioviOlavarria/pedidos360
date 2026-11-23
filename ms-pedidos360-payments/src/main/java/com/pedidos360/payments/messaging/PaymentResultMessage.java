package com.pedidos360.payments.messaging;

import com.pedidos360.payments.domain.Payment;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Mensaje del tópico {@link PaymentEventPublisher#TOPIC} (clave = orderId), serializado como JSON.
 * Debe mantenerse compatible con com.pedidos360.orders.messaging.kafka.PaymentResultMessage.
 */
public record PaymentResultMessage(
        Long paymentId,
        Long orderId,
        String status,
        BigDecimal amount,
        String provider,
        String providerReference,
        String paymentMethod,
        Instant occurredAt) {

    public static PaymentResultMessage of(Payment payment) {
        return new PaymentResultMessage(payment.getId(), payment.getOrderId(), payment.getStatus().name(),
                payment.getAmount(), payment.getProvider(), payment.getProviderReference(), payment.getPaymentMethod(),
                payment.getCompletedAt() != null ? payment.getCompletedAt() : Instant.now());
    }
}
