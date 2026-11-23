package com.pedidos360.kafkaadmin;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Map;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
@EmbeddedKafka(kraft = false, count = 2, partitions = 2, topics = "groups-test")
class ConsumerGroupControllerTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Test
    void inspectsLagResetsOffsetsAndDeletesGroups() throws Exception {
        KafkaTemplate<String, String> producer = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                KafkaTestUtils.producerProps(broker), new StringSerializer(), new StringSerializer()));
        for (int i = 0; i < 4; i++) {
            producer.send("groups-test", i % 2, "k" + i, "v" + i).get();
        }
        producer.destroy();

        // Un grupo que confirmó 1 mensaje por partición: lag = 1 en cada una.
        Map<String, Object> props = KafkaTestUtils.consumerProps("analitica", "false", broker);
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(props,
                new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            consumer.commitSync(Map.of(
                    new TopicPartition("groups-test", 0), new OffsetAndMetadata(1),
                    new TopicPartition("groups-test", 1), new OffsetAndMetadata(1)));
        }

        mvc.perform(get("/api/consumer-groups")).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].groupId", hasItem("analitica")));
        mvc.perform(get("/api/consumer-groups/analitica")).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("EMPTY"))
                .andExpect(jsonPath("$.totalLag").value(2));

        mvc.perform(post("/api/consumer-groups/analitica/reset-offsets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topic\":\"groups-test\",\"to\":\"earliest\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/consumer-groups/analitica")).andExpect(jsonPath("$.totalLag").value(4));

        mvc.perform(post("/api/consumer-groups/analitica/reset-offsets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topic\":\"groups-test\",\"to\":\"mañana\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(delete("/api/consumer-groups/analitica")).andExpect(status().isNoContent());
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                mvc.perform(get("/api/consumer-groups/analitica")).andExpect(status().isNotFound()));
    }
}
