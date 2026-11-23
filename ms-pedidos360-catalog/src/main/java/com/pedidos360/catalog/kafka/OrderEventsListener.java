package com.pedidos360.catalog.kafka;

import com.pedidos360.catalog.service.SalesProjectionService;
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
 * Consumidor del tópico {@link KafkaTopics#ORDER_EVENTS} en el grupo pedidos360-catalog-sales.
 * Tiene su propio grupo, distinto al de notifications: ambos reciben todos los eventos y cada uno
 * cumple su responsabilidad (aquí, la estadística de ventas). Solo valida y delega en
 * {@link SalesProjectionService}; si la base de datos falla, el error handler reintenta y luego envía al DLT.
 */
@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class OrderEventsListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsListener.class);

    private final SalesProjectionService salesProjection;
    private final Validator validator;

    public OrderEventsListener(SalesProjectionService salesProjection, Validator validator) {
        this.salesProjection = salesProjection;
        this.validator = validator;
    }

    @KafkaListener(topics = KafkaTopics.ORDER_EVENTS, groupId = "${spring.kafka.consumer.group-id}",
            concurrency = "${app.kafka.listener-concurrency:3}")
    public void onOrderEvent(@Payload OrderEventMessage event,
                             @Header(KafkaHeaders.RECEIVED_KEY) String key,
                             @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                             @Header(KafkaHeaders.OFFSET) long offset) {
        Set<ConstraintViolation<OrderEventMessage>> violations = validator.validate(event);
        if (!violations.isEmpty()) {
            String detail = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new InvalidEventException("Evento de pedido inválido (clave " + key + "): " + detail);
        }
        if (!event.orderId().toString().equals(key)) {
            throw new InvalidEventException("La clave " + key + " no corresponde al pedido #" + event.orderId());
        }
        log.info("Kafka: evento {} del pedido #{} recibido de {}-{}@{}", event.type(), event.orderId(),
                KafkaTopics.ORDER_EVENTS, partition, offset);
        salesProjection.apply(event);
    }
}
