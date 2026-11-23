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
 * Consumidor del tópico {@link KafkaTopics#PAYMENT_EVENTS} (grupo pedidos360-orders): aplica el resultado del pago
 * al pedido a través de {@link OrderService#applyPaymentResult}. Payload inválido → DLT sin reintentos;
 * errores transitorios (base de datos, catálogo) → reintentos con backoff y luego DLT.
 */
@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class PaymentResultListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentResultListener.class);

    private final OrderService orderService;
    private final Validator validator;

    public PaymentResultListener(OrderService orderService, Validator validator) {
        this.orderService = orderService;
        this.validator = validator;
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_EVENTS, groupId = "${spring.kafka.consumer.group-id}",
            concurrency = "${app.kafka.listener-concurrency:3}",
            // Este tópico trae otro DTO que el de boletas (tipo por defecto del consumidor).
            properties = "spring.json.value.default.type=com.pedidos360.orders.messaging.kafka.PaymentResultMessage")
    public void onPaymentResult(@Payload PaymentResultMessage message,
                                @Header(KafkaHeaders.RECEIVED_KEY) String key,
                                @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                @Header(KafkaHeaders.OFFSET) long offset) {
        Set<ConstraintViolation<PaymentResultMessage>> violations = validator.validate(message);
        if (!violations.isEmpty()) {
            String detail = violations.stream().map(v -> v.getPropertyPath() + " " + v.getMessage()).sorted()
                    .collect(Collectors.joining(", "));
            throw new InvalidEventException("Resultado de pago inválido (clave " + key + "): " + detail);
        }
        if (!message.orderId().toString().equals(key)) {
            throw new InvalidEventException("La clave " + key + " no corresponde al pedido #" + message.orderId());
        }
        log.info("Kafka: pago {} del pedido #{} = {} ({}-{}@{})", message.reference(), message.orderId(),
                message.status(), KafkaTopics.PAYMENT_EVENTS, partition, offset);
        orderService.applyPaymentResult(message.orderId(), message.status(), message.amount(), message.reference(),
                message.occurredAt());
    }
}
