package com.pedidos360.orders.config;

import com.pedidos360.orders.messaging.kafka.InvalidEventException;
import com.pedidos360.orders.messaging.kafka.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuración Kafka de orders. Las propiedades del cliente (bootstrap servers, serializers, grupo, acks,
 * idempotencia, etc.) están en application.yml bajo spring.kafka; aquí se declaran los tópicos y el manejo
 * de errores de los consumidores.
 *
 * Solo se activa con app.kafka.enabled=true (en Docker siempre; en local se puede omitir Kafka).
 */
@Configuration
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    /** orders es dueño del flujo de eventos de pedidos: 7 días de historia para analítica y reprocesos. */
    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name(KafkaTopics.ORDER_EVENTS)
                .partitions(KafkaTopics.PARTITIONS)
                .replicas(KafkaTopics.REPLICAS)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(Duration.ofDays(7).toMillis()))
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "1")
                .config(TopicConfig.MAX_MESSAGE_BYTES_CONFIG, "1048576")
                .build();
    }

    /** DLT del tópico que consume orders: se conserva 30 días para revisar y reprocesar a mano. */
    @Bean
    public NewTopic billingReceiptsDeadLetterTopic() {
        return TopicBuilder.name(KafkaTopics.BILLING_RECEIPTS + KafkaTopics.DLT_SUFFIX)
                .partitions(1)
                .replicas(KafkaTopics.REPLICAS)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(Duration.ofDays(30).toMillis()))
                .build();
    }

    /**
     * Manejo de errores de todos los @KafkaListener (Spring Boot lo asigna al container factory por defecto):
     * 3 reintentos con backoff exponencial (1s, 2s, 4s) y luego el registro se publica en "&lt;tópico&gt;.DLT"
     * con los headers de la excepción. Payloads inválidos o no deserializables van directo al DLT.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(ProducerFactory<Object, Object> producerFactory) {
        // Los registros que no se pudieron deserializar llegan como byte[]: se reenvían tal cual al DLT.
        Map<Class<?>, KafkaOperations<?, ?>> templates = new LinkedHashMap<>();
        templates.put(byte[].class, new KafkaTemplate<>(producerFactory,
                Map.of(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class)));
        templates.put(Object.class, new KafkaTemplate<>(producerFactory));

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(templates,
                (record, ex) -> {
                    log.error("Kafka: registro {}-{}@{} (clave {}) enviado a DLT: {}", record.topic(), record.partition(),
                            record.offset(), record.key(), rootCause(ex));
                    return new TopicPartition(record.topic() + KafkaTopics.DLT_SUFFIX, -1);
                });

        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(1_000);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(10_000);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(InvalidEventException.class, IllegalArgumentException.class);
        handler.setRetryListeners((record, ex, attempt) ->
                log.warn("Kafka: fallo procesando {}-{}@{} (clave {}), intento {}: {}", record.topic(), record.partition(),
                        record.offset(), record.key(), attempt, rootCause(ex)));
        return handler;
    }

    private static String rootCause(Throwable ex) {
        return NestedExceptionUtils.getMostSpecificCause(ex).toString();
    }
}
