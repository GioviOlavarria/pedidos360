package com.pedidos360.orders.messaging.kafka;

import com.pedidos360.orders.domain.model.service.OrderService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Consumidor del tópico {@link KafkaTopics#BILLING_RECEIPTS} (grupo pedidos360-orders).
 * Solo traduce el mensaje: valida el payload y delega la lógica de negocio en {@link OrderService}.
 * Un payload inválido lanza {@link InvalidEventException} (va directo al DLT); cualquier otro error
 * (p. ej. base de datos caída) se reintenta con backoff exponencial antes de ir al DLT.
 */
@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class ReceiptIssuedListener {

    private static final Logger log = LoggerFactory.getLogger(ReceiptIssuedListener.class);

    private final OrderService orderService;
    private final Validator validator;

    public ReceiptIssuedListener(OrderService orderService, Validator validator) {
        this.orderService = orderService;
        this.validator = validator;
    }

    @KafkaListener(topics = KafkaTopics.BILLING_RECEIPTS, groupId = "${spring.kafka.consumer.group-id}",
            concurrency = "${app.kafka.listener-concurrency:3}")
    public void onReceiptIssued(@Payload(required = false) ReceiptIssuedMessage message,
                                @Header(KafkaHeaders.RECEIVED_KEY) String key,
                                @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                @Header(KafkaHeaders.OFFSET) long offset) {
        if (message == null) {
            // Tombstone del tópico compactado: la boleta del pedido fue anulada/eliminada.
            log.info("Kafka: tombstone de boleta para pedido {} ({}-{}@{}), se ignora", key, KafkaTopics.BILLING_RECEIPTS, partition, offset);
            return;
        }
        validate(message, key);
        log.info("Kafka: boleta folio {} del pedido #{} recibida ({}-{}@{})",
                message.folio(), message.orderId(), KafkaTopics.BILLING_RECEIPTS, partition, offset);
        orderService.registerReceipt(message.orderId(), message.folio(), message.trackId(), message.siiStatus(),
                message.issuedAt());
    }

    private void validate(ReceiptIssuedMessage message, String key) {
        Set<ConstraintViolation<ReceiptIssuedMessage>> violations = validator.validate(message);
        if (!violations.isEmpty()) {
            String detail = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new InvalidEventException("Boleta inválida para clave " + key + ": " + detail);
        }
        if (!message.orderId().toString().equals(key)) {
            throw new InvalidEventException("La clave " + key + " no corresponde al pedido #" + message.orderId());
        }
    }
}
