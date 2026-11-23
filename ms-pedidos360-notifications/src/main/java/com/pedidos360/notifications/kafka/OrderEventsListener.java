package com.pedidos360.notifications.kafka;

import com.pedidos360.notifications.service.OrderStatusMailService;
import jakarta.mail.MessagingException;
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
 * Consumidor del tópico {@link KafkaTopics#ORDER_EVENTS} en el grupo pedidos360-notifications.
 * catalog lee el mismo tópico con su propio grupo: cada grupo recibe todos los eventos (publish/subscribe) y,
 * dentro de cada grupo, las 3 particiones se reparten entre sus hilos/instancias (balanceo de carga).
 *
 * Aquí solo se valida y se enruta; la lógica (armar y enviar el correo) está en {@link OrderStatusMailService}.
 * Un fallo SMTP se reintenta con backoff y, agotados los reintentos, el evento queda en el DLT.
 */
@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class OrderEventsListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsListener.class);

    private final OrderStatusMailService statusMailService;
    private final Validator validator;

    public OrderEventsListener(OrderStatusMailService statusMailService, Validator validator) {
        this.statusMailService = statusMailService;
        this.validator = validator;
    }

    @KafkaListener(topics = KafkaTopics.ORDER_EVENTS, groupId = "${spring.kafka.consumer.group-id}",
            concurrency = "${app.kafka.listener-concurrency:3}")
    public void onOrderEvent(@Payload OrderEventMessage event,
                             @Header(KafkaHeaders.RECEIVED_KEY) String key,
                             @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                             @Header(KafkaHeaders.OFFSET) long offset) throws MessagingException {
        validate(event, key);
        log.info("Kafka: evento {} del pedido #{} (estado {}) recibido de {}-{}@{}", event.type(), event.orderId(),
                event.status(), KafkaTopics.ORDER_EVENTS, partition, offset);

        if (event.type() != OrderEventType.STATUS_CHANGED) {
            // La boleta de un pedido nuevo la envía el flujo RabbitMQ; ediciones y eliminaciones no notifican.
            return;
        }
        if ("PENDIENTE_PAGO".equals(event.previousStatus()) && "CREADO".equals(event.status())) {
            // Pago confirmado: el cliente recibe la boleta por RabbitMQ, no hace falta otro correo.
            return;
        }
        if (event.customerEmail() == null || !event.customerEmail().contains("@")) {
            log.warn("Pedido #{} sin correo: no se notifica el cambio a {}", event.orderId(), event.status());
            return;
        }
        statusMailService.sendStatusChanged(event);
    }

    private void validate(OrderEventMessage event, String key) {
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
    }
}
