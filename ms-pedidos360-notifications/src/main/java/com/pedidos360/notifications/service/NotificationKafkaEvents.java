package com.pedidos360.notifications.service;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class NotificationKafkaEvents {

    private static final Logger log = LoggerFactory.getLogger(NotificationKafkaEvents.class);

    private final KafkaTemplate<String, String> kafka;
    private final String receiptSentTopic;

    public NotificationKafkaEvents(KafkaTemplate<String, String> kafka,
                                   @Value("${app.kafka.receipt-sent-topic}") String receiptSentTopic) {
        this.kafka = kafka;
        this.receiptSentTopic = receiptSentTopic;
    }

    @KafkaListener(topics = "${app.kafka.order-created-topic}")
    public void onOrderCreated(ConsumerRecord<String, String> order) {
        log.info("Kafka registró la creación del pedido #{} a las {}", order.key(), order.value());
    }

    public void publishReceiptSent(Long orderId) {
        try {
            kafka.send(receiptSentTopic, orderId.toString(), Instant.now().toString())
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.warn("Falló la confirmación en Kafka de la boleta del pedido #{}", orderId, error);
                        }
                    });
        } catch (RuntimeException error) {
            log.warn("Falló la confirmación en Kafka de la boleta del pedido #{}", orderId, error);
        }
    }
}
