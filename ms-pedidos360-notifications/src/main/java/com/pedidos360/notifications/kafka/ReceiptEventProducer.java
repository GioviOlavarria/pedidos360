package com.pedidos360.notifications.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Productor del tópico {@link KafkaTopics#BILLING_RECEIPTS}. La clave es el orderId: el tópico es compactado,
 * así que Kafka conserva la última boleta de cada pedido. El envío es asíncrono con reintentos del productor
 * (idempotente, acks=all); si se agota delivery.timeout.ms el fallo queda en el log y la boleta ya fue enviada
 * por correo, por lo que el cliente no se ve afectado.
 */
@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class ReceiptEventProducer {

    private static final Logger log = LoggerFactory.getLogger(ReceiptEventProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public ReceiptEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(ReceiptIssuedMessage message) {
        try {
            kafkaTemplate.send(KafkaTopics.BILLING_RECEIPTS, message.orderId().toString(), message)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.error("Kafka: no se pudo publicar la boleta folio {} del pedido #{}: {}",
                                    message.folio(), message.orderId(), error.getMessage());
                        } else {
                            log.info("Kafka: boleta folio {} del pedido #{} publicada en {}-{}@{}", message.folio(),
                                    message.orderId(), result.getRecordMetadata().topic(),
                                    result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                        }
                    });
        } catch (RuntimeException e) {
            log.error("Kafka: no se pudo publicar la boleta del pedido #{}: {}", message.orderId(), e.getMessage());
        }
    }
}
