package com.pedidos360.payments.messaging;

import com.pedidos360.payments.domain.Payment;
import com.pedidos360.payments.domain.PaymentRepository;
import com.pedidos360.payments.domain.PaymentStatus;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Productor del tópico pedidos360.payments.events. orders lo consume para marcar el pedido como pagado
 * (y emitir la boleta) o cancelarlo.
 *
 * Entrega garantizada: el resultado se guarda primero en la base de datos con event_published=false; si Kafka
 * no confirma la escritura (acks=all), un job lo vuelve a publicar cada 30 segundos hasta lograrlo.
 * orders aplica los resultados de forma idempotente, así que reenviar es seguro.
 */
@Configuration
public class PaymentEventPublisher {

    public static final String TOPIC = "pedidos360.payments.events";

    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final PaymentRepository payments;

    public PaymentEventPublisher(KafkaTemplate<String, Object> kafkaTemplate, PaymentRepository payments) {
        this.kafkaTemplate = kafkaTemplate;
        this.payments = payments;
    }

    /** payments es dueño del tópico: 3 particiones, 2 réplicas y 30 días de historia de pagos. */
    @Bean
    public NewTopic paymentEventsTopic() {
        return TopicBuilder.name(TOPIC)
                .partitions(3)
                .replicas(2)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(Duration.ofDays(30).toMillis()))
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "1")
                .build();
    }

    /** Publica el resultado y marca el pago como publicado. Devuelve false si Kafka no lo confirmó. */
    public boolean publish(Payment payment) {
        PaymentResultMessage message = PaymentResultMessage.of(payment);
        try {
            var result = kafkaTemplate.send(TOPIC, payment.getOrderId().toString(), message).get(15, TimeUnit.SECONDS);
            payment.markEventPublished();
            payments.save(payment);
            log.info("Kafka: pago {} del pedido #{} = {} publicado en {}-{}@{}", payment.getId(), payment.getOrderId(),
                    payment.getStatus(), TOPIC, result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.error("Kafka: no se pudo publicar el pago {} del pedido #{} (se reintentará): {}", payment.getId(),
                    payment.getOrderId(), e.getMessage());
            return false;
        }
    }

    @Scheduled(fixedDelayString = "PT30S", initialDelayString = "PT30S")
    public void republishPending() {
        payments.findByEventPublishedFalseAndStatusNotAndCompletedAtBefore(PaymentStatus.PENDING,
                Instant.now().minusSeconds(20)).forEach(this::publish);
    }
}
