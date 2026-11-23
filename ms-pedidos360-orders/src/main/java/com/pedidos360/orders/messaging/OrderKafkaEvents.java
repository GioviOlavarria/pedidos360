package com.pedidos360.orders.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class OrderKafkaEvents {

    private static final Logger log = LoggerFactory.getLogger(OrderKafkaEvents.class);

    private final KafkaTemplate<String, String> kafka;
    private final String orderCreatedTopic;

    public OrderKafkaEvents(KafkaTemplate<String, String> kafka,
                            @Value("${app.kafka.order-created-topic}") String orderCreatedTopic) {
        this.kafka = kafka;
        this.orderCreatedTopic = orderCreatedTopic;
    }

    @TransactionalEventListener
    public void publishOrderCreated(OrderCreatedEvent event) {
        try {
            kafka.send(orderCreatedTopic, event.orderId().toString(), event.createdAt().toString())
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.warn("Falló la publicación en Kafka del pedido #{}", event.orderId(), error);
                        }
                    });
        } catch (RuntimeException error) {
            log.warn("Falló la publicación en Kafka del pedido #{}", event.orderId(), error);
        }
    }

    @KafkaListener(topics = "${app.kafka.receipt-sent-topic}")
    public void onReceiptSent(ConsumerRecord<String, String> receipt) {
        log.info("Kafka confirmó el envío de la boleta del pedido #{} a las {}", receipt.key(), receipt.value());
    }
}
