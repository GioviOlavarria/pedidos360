package com.pedidos360.orders.messaging.kafka;

import com.pedidos360.orders.api.dto.OrderItemRequest;
import com.pedidos360.orders.api.dto.OrderRequest;
import com.pedidos360.orders.client.CatalogClient;
import com.pedidos360.orders.client.CatalogClient.ReservedItem;
import com.pedidos360.orders.domain.model.Order;
import com.pedidos360.orders.domain.model.OrderStatus;
import com.pedidos360.orders.domain.model.service.CurrentUser;
import com.pedidos360.orders.domain.model.service.OrderService;
import com.pedidos360.orders.repository.OrderRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Prueba de punta a punta contra un clúster Kafka embebido de 2 brokers (con ZooKeeper, como en Docker):
 * publicación del evento de pedido, consumo de la boleta y envío al DLT de los mensajes defectuosos.
 */
@SpringBootTest(properties = {
        "app.kafka.enabled=true",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@EmbeddedKafka(kraft = false, count = 2, partitions = KafkaTopics.PARTITIONS,
        topics = {KafkaTopics.BILLING_RECEIPTS, KafkaTopics.PAYMENT_EVENTS})
@ActiveProfiles("test")
class OrderKafkaIntegrationTest {

    private static final CurrentUser CUSTOMER = new CurrentUser("cliente@outlook.com", "Cliente", false, false);

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository repository;

    @MockBean
    private CatalogClient catalogClient;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    private KafkaTemplate<String, String> rawProducer;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        when(catalogClient.reserve(any()))
                .thenReturn(List.of(new ReservedItem(7L, "Caja", new BigDecimal("2990.00"), 2)));
        rawProducer = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                KafkaTestUtils.producerProps(broker), new StringSerializer(), new StringSerializer()));
    }

    @AfterEach
    void tearDown() {
        rawProducer.destroy();
    }

    @Test
    void publishesOrderLifecycleEventsKeyedByOrderId() {
        Order order = orderService.createOrder(request(), CUSTOMER);
        orderService.markPaid(order.getId(), "SIMULADO-1", java.time.Instant.now());

        // Otros tests de la clase también publican en el tópico: se filtra por la clave de este pedido.
        String key = order.getId().toString();
        List<ConsumerRecord<String, String>> records = readAll(KafkaTopics.ORDER_EVENTS, 2, key);
        assertEquals(2, records.size());
        // Misma clave => misma partición => orden garantizado.
        assertEquals(records.get(0).partition(), records.get(1).partition());
        assertTrue(records.get(0).value().contains("\"type\":\"CREATED\""));
        assertTrue(records.get(1).value().contains("\"type\":\"STATUS_CHANGED\""));
        assertTrue(records.get(1).value().contains("\"previousStatus\":\"PENDIENTE_PAGO\""));
    }

    @Test
    void consumesIssuedReceiptAndUpdatesOrder() throws Exception {
        Order order = orderService.createOrder(request(), CUSTOMER);
        String json = """
                {"orderId":%d,"documentType":39,"folio":%d,"trackId":"SIM-1","netAmount":5025,"taxAmount":955,
                 "total":5980,"siiStatus":"SIMULADO","emailedTo":"cliente@outlook.com","issuedAt":"2026-11-23T12:00:00Z"}
                """.formatted(order.getId(), order.getId());

        rawProducer.send(KafkaTopics.BILLING_RECEIPTS, order.getId().toString(), json).get();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            Order updated = repository.findById(order.getId()).orElseThrow();
            assertEquals(order.getId(), updated.getReceiptFolio());
            assertEquals("SIM-1", updated.getReceiptTrackId());
        });
    }

    @Test
    void paymentEventsMarkOrdersPaidOrCancelled() throws Exception {
        Order paid = orderService.createOrder(request(), CUSTOMER);
        Order rejected = orderService.createOrder(request(), CUSTOMER);
        rawProducer.send(KafkaTopics.PAYMENT_EVENTS, paid.getId().toString(), payment(paid.getId(), "PAID", 5980)).get();
        rawProducer.send(KafkaTopics.PAYMENT_EVENTS, rejected.getId().toString(), payment(rejected.getId(), "REJECTED", 5980)).get();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertEquals(OrderStatus.CREADO, repository.findById(paid.getId()).orElseThrow().getStatus());
            assertEquals(OrderStatus.CANCELADO, repository.findById(rejected.getId()).orElseThrow().getStatus());
        });
        assertEquals("FLOW-777", repository.findById(paid.getId()).orElseThrow().getPaymentReference());
    }

    @Test
    void underpaidPaymentGoesToDeadLetterTopic() throws Exception {
        Order order = orderService.createOrder(request(), CUSTOMER);
        rawProducer.send(KafkaTopics.PAYMENT_EVENTS, order.getId().toString(), payment(order.getId(), "PAID", 10)).get();

        List<ConsumerRecord<String, String>> dead = readAll(KafkaTopics.PAYMENT_EVENTS + KafkaTopics.DLT_SUFFIX, 1);
        assertEquals(order.getId().toString(), dead.getFirst().key());
        assertEquals(OrderStatus.PENDIENTE_PAGO, repository.findById(order.getId()).orElseThrow().getStatus());
    }

    private static String payment(Long orderId, String status, int amount) {
        return """
                {"paymentId":1,"orderId":%d,"status":"%s","amount":%d,"provider":"FLOW","providerReference":"777",
                 "paymentMethod":"Webpay","occurredAt":"2026-11-23T12:00:00Z"}
                """.formatted(orderId, status, amount);
    }

    @Test
    void malformedAndInvalidReceiptsGoToDeadLetterTopic() throws Exception {
        rawProducer.send(KafkaTopics.BILLING_RECEIPTS, "900", "esto-no-es-json").get();
        rawProducer.send(KafkaTopics.BILLING_RECEIPTS, "901", "{\"orderId\":901,\"documentType\":39}").get();

        List<ConsumerRecord<String, String>> dead = readAll(KafkaTopics.BILLING_RECEIPTS + KafkaTopics.DLT_SUFFIX, 2);
        assertEquals(List.of("900", "901"), dead.stream().map(ConsumerRecord::key).sorted().toList());
        assertTrue(dead.stream().allMatch(r -> r.headers().lastHeader("kafka_dlt-exception-message") != null));
    }

    private List<ConsumerRecord<String, String>> readAll(String topic, int expected) {
        return readAll(topic, expected, null);
    }

    private List<ConsumerRecord<String, String>> readAll(String topic, int expected, String key) {
        Map<String, Object> props = KafkaTestUtils.consumerProps("test-" + topic + System.nanoTime(), "false", broker);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(props,
                new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            consumer.subscribe(List.of(topic));
            List<ConsumerRecord<String, String>> result = new ArrayList<>();
            long deadline = System.currentTimeMillis() + 30_000;
            while (result.size() < expected && System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> polled = consumer.poll(Duration.ofMillis(500));
                polled.forEach(r -> {
                    if (key == null || key.equals(r.key())) result.add(r);
                });
            }
            result.sort((a, b) -> Long.compare(a.timestamp(), b.timestamp()));
            return result;
        }
    }

    private static OrderRequest request() {
        OrderRequest request = new OrderRequest();
        request.setCustomerId(1L);
        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(7L);
        item.setQuantity(2);
        request.setItems(List.of(item));
        return request;
    }
}
