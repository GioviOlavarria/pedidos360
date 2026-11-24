package com.pedidos360.notifications.kafka;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Consumo del tópico de eventos de pedidos contra un clúster Kafka embebido de 2 brokers:
 * aviso por correo de cambios de estado, payloads inválidos al DLT y reintentos ante fallas SMTP.
 */
@SpringBootTest(properties = {
        "app.kafka.enabled=true",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "management.health.mail.enabled=false"
})
@EmbeddedKafka(kraft = false, count = 2, partitions = KafkaTopics.PARTITIONS, topics = {KafkaTopics.ORDER_EVENTS})
class OrderEventsKafkaIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker broker;

    @MockBean
    private JavaMailSender mailSender;

    private KafkaTemplate<String, String> producer;

    @BeforeEach
    void setUp() {
        reset(mailSender);
        when(mailSender.createMimeMessage()).thenAnswer(i -> new MimeMessage((Session) null));
        producer = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                KafkaTestUtils.producerProps(broker), new StringSerializer(), new StringSerializer()));
    }

    @AfterEach
    void tearDown() {
        producer.destroy();
    }

    @Test
    void statusChangeSendsMailAndOtherEventsDoNot() throws Exception {
        producer.send(KafkaTopics.ORDER_EVENTS, "10", event(10, "CREATED", "CREADO", null, "a@outlook.com")).get();
        // Pago confirmado: no genera aviso (la boleta llega por RabbitMQ).
        producer.send(KafkaTopics.ORDER_EVENTS, "10", event(10, "STATUS_CHANGED", "CREADO", "PENDIENTE_PAGO", "a@outlook.com")).get();
        producer.send(KafkaTopics.ORDER_EVENTS, "10", event(10, "STATUS_CHANGED", "ACEPTADO", "CREADO", "a@outlook.com")).get();

        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> verify(mailSender, times(1)).send(any(MimeMessage.class)));
    }

    @Test
    void invalidEventsGoStraightToDeadLetterTopic() throws Exception {
        producer.send(KafkaTopics.ORDER_EVENTS, "20", "{malformado").get();
        // Falta eventId/occurredAt: no cumple el contrato.
        producer.send(KafkaTopics.ORDER_EVENTS, "21", "{\"orderId\":21,\"type\":\"STATUS_CHANGED\",\"status\":\"ACEPTADO\"}").get();

        List<ConsumerRecord<String, String>> dead = readDlt(keys -> keys.containsAll(List.of("20", "21")));
        assertTrue(dead.stream().map(ConsumerRecord::key).toList().containsAll(List.of("20", "21")));
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void smtpFailureIsRetriedThenSentToDeadLetterTopic() throws Exception {
        doThrow(new MailSendException("SMTP caído")).when(mailSender).send(any(MimeMessage.class));

        producer.send(KafkaTopics.ORDER_EVENTS, "30", event(30, "STATUS_CHANGED", "DESPACHADO", "EN_PREPARACION", "b@outlook.com")).get();

        List<ConsumerRecord<String, String>> dead = readDlt(keys -> keys.contains("30"));
        ConsumerRecord<String, String> record = dead.stream().filter(r -> r.key().equals("30")).findFirst().orElseThrow();
        assertTrue(new String(record.headers().lastHeader("kafka_dlt-exception-cause-fqcn").value())
                .contains("MailSendException"));
        // 1 intento + 3 reintentos.
        verify(mailSender, atLeast(4)).send(any(MimeMessage.class));
    }

    private List<ConsumerRecord<String, String>> readDlt(java.util.function.Predicate<List<String>> done) {
        Map<String, Object> props = KafkaTestUtils.consumerProps("dlt-reader-" + System.nanoTime(), "false", broker);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(props,
                new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            consumer.subscribe(List.of(KafkaTopics.ORDER_EVENTS + KafkaTopics.DLT_SUFFIX));
            List<ConsumerRecord<String, String>> result = new ArrayList<>();
            long deadline = System.currentTimeMillis() + 60_000;
            while (!done.test(result.stream().map(ConsumerRecord::key).toList()) && System.currentTimeMillis() < deadline) {
                consumer.poll(Duration.ofMillis(500)).forEach(result::add);
            }
            assertEquals(true, done.test(result.stream().map(ConsumerRecord::key).toList()), "Registros esperados en el DLT");
            return result;
        }
    }

    private static String event(long orderId, String type, String status, String previous, String email) {
        return """
                {"eventId":"%s","type":"%s","orderId":%d,"status":"%s","previousStatus":%s,
                 "customerEmail":"%s","customerName":"Cliente","total":5980,
                 "items":[{"productId":7,"productName":"Caja","quantity":2,"unitPrice":2990,"subtotal":5980}],
                 "occurredAt":"2026-11-23T12:00:00Z"}
                """.formatted(java.util.UUID.randomUUID(), type, orderId, status,
                previous == null ? "null" : "\"" + previous + "\"", email);
    }
}
