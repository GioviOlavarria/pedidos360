package com.pedidos360.orders.messaging.kafka;

import com.pedidos360.orders.messaging.OrderChangedEvent;
import com.pedidos360.orders.messaging.OrderCreatedEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Productor del tópico {@link KafkaTopics#ORDER_EVENTS}. Publica solo DESPUÉS del commit, igual que el
 * publicador de RabbitMQ: nunca se anuncia un cambio que no quedó guardado.
 * El envío es asíncrono; los reintentos los hace el propio productor (idempotente, acks=all) dentro de
 * delivery.timeout.ms. Si Kafka no está disponible el pedido se guarda igual y el fallo queda en el log.
 */
@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class OrderEventProducer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventProducer.class);
    static final String EVENT_TYPE_HEADER = "eventType";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OrderEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderCreatedEvent event) {
        List<OrderEventMessage.Item> items = event.items().stream()
                .map(i -> new OrderEventMessage.Item(i.productId(), i.productName(), i.quantity(), i.unitPrice(), i.subtotal()))
                .toList();
        publish(new OrderEventMessage(UUID.randomUUID(), OrderEventType.CREATED, event.orderId(), "CREADO", null,
                event.customerEmail(), event.customerName(), event.total(), items, Instant.now()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderChanged(OrderChangedEvent event) {
        List<OrderEventMessage.Item> items = event.items().stream()
                .map(i -> new OrderEventMessage.Item(i.productId(), i.productName(), i.quantity(), i.unitPrice(), i.subtotal()))
                .toList();
        publish(new OrderEventMessage(UUID.randomUUID(), event.type(), event.orderId(), event.status().name(),
                event.previousStatus() != null ? event.previousStatus().name() : null,
                event.customerEmail(), event.customerName(), event.total(), items, Instant.now()));
    }

    void publish(OrderEventMessage message) {
        ProducerRecord<String, Object> record =
                new ProducerRecord<>(KafkaTopics.ORDER_EVENTS, message.orderId().toString(), message);
        record.headers().add(EVENT_TYPE_HEADER, message.type().name().getBytes(StandardCharsets.UTF_8));
        try {
            kafkaTemplate.send(record).whenComplete((result, error) -> {
                if (error != null) {
                    log.error("Kafka: no se pudo publicar {} del pedido #{} (eventId={}): {}",
                            message.type(), message.orderId(), message.eventId(), error.getMessage());
                } else {
                    log.info("Kafka: {} del pedido #{} publicado en {}-{}@{}", message.type(), message.orderId(),
                            result.getRecordMetadata().topic(), result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                }
            });
        } catch (RuntimeException e) {
            // max.block.ms agotado (sin metadata del clúster) o error de serialización.
            log.error("Kafka: no se pudo publicar {} del pedido #{}: {}", message.type(), message.orderId(), e.getMessage());
        }
    }
}
