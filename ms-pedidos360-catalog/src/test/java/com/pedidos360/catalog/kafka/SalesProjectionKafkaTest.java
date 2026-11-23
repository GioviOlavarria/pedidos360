package com.pedidos360.catalog.kafka;

import com.pedidos360.catalog.repository.OrderSalesLineRepository;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** El grupo pedidos360-catalog-sales proyecta los eventos de pedidos en la estadística de ventas. */
@SpringBootTest(properties = {
        "app.kafka.enabled=true",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@AutoConfigureMockMvc
@EmbeddedKafka(kraft = false, count = 2, partitions = KafkaTopics.PARTITIONS, topics = {KafkaTopics.ORDER_EVENTS})
@ActiveProfiles("test")
class SalesProjectionKafkaTest {

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private OrderSalesLineRepository repository;

    private KafkaTemplate<String, String> producer;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        producer = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                KafkaTestUtils.producerProps(broker), new StringSerializer(), new StringSerializer()));
    }

    @AfterEach
    void tearDown() {
        producer.destroy();
    }

    @Test
    void projectsSalesAndIsIdempotentAndHandlesCancellation() throws Exception {
        String created = event(1, "CREATED", "CREADO", 3);
        producer.send(KafkaTopics.ORDER_EVENTS, "1", created).get();
        producer.send(KafkaTopics.ORDER_EVENTS, "1", created).get(); // reentrega: no debe duplicar
        producer.send(KafkaTopics.ORDER_EVENTS, "2", event(2, "CREATED", "CREADO", 2)).get();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                mvc.perform(get("/api/catalog/sales-stats").with(admin()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$", hasSize(1)))
                        .andExpect(jsonPath("$[0].unitsSold").value(5))
                        .andExpect(jsonPath("$[0].orders").value(2)));

        producer.send(KafkaTopics.ORDER_EVENTS, "2", event(2, "STATUS_CHANGED", "CANCELADO", 2)).get();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                mvc.perform(get("/api/catalog/sales-stats").with(admin()))
                        .andExpect(jsonPath("$[0].unitsSold").value(3))
                        .andExpect(jsonPath("$[0].orders").value(1)));
    }

    @Test
    void salesStatsRequireAdmin() throws Exception {
        mvc.perform(get("/api/catalog/sales-stats")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/catalog/sales-stats").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Customer"))))
                .andExpect(status().isForbidden());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_Admin"));
    }

    private static String event(long orderId, String type, String status, int quantity) {
        return """
                {"eventId":"%s","type":"%s","orderId":%d,"status":"%s","customerEmail":"a@outlook.com",
                 "total":%d,"items":[{"productId":7,"productName":"Caja","quantity":%d,"unitPrice":1000,"subtotal":%d}],
                 "occurredAt":"2026-11-23T12:00:00Z"}
                """.formatted(UUID.randomUUID(), type, orderId, status, quantity * 1000, quantity, quantity * 1000);
    }
}
