package com.pedidos360.notifications.kafka;

import com.pedidos360.notifications.sii.BoletaElectronica;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Mensaje del tópico {@link KafkaTopics#BILLING_RECEIPTS} (clave = orderId): boleta emitida ante el SII y enviada
 * al cliente. Debe mantenerse compatible con com.pedidos360.orders.messaging.kafka.ReceiptIssuedMessage.
 */
public record ReceiptIssuedMessage(
        Long orderId,
        Integer documentType,
        Long folio,
        String trackId,
        BigDecimal netAmount,
        BigDecimal taxAmount,
        BigDecimal total,
        String siiStatus,
        String emailedTo,
        Instant issuedAt) {

    public static ReceiptIssuedMessage of(Long orderId, BoletaElectronica boleta, String emailedTo) {
        return new ReceiptIssuedMessage(orderId, boleta.documentType(), boleta.folio(), boleta.trackId(),
                boleta.netAmount(), boleta.taxAmount(), boleta.total(), boleta.siiStatus(), emailedTo, boleta.issuedAt());
    }
}
