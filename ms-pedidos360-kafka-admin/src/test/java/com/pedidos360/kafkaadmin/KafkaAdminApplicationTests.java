package com.pedidos360.kafkaadmin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
@EmbeddedKafka(kraft = false, count = 2, brokerProperties = {
        "auto.create.topics.enable=false", "num.network.threads=2", "num.io.threads=2"
})
class KafkaAdminApplicationTests {

    @Autowired
    private MockMvc mvc;

    @Test
    void managesReplicatedTopicsAndTheirConfigOverrides() throws Exception {
        String topic = "/api/topics/admin-test";
        String create = """
                {"name":"admin-test","partitions":1,"replicationFactor":2,"configs":{"retention.ms":"60000"}}
                """;

        mvc.perform(post("/api/topics").contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost" + topic));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            mvc.perform(get("/api/topics")).andExpect(jsonPath("$", hasItem("admin-test")));
            mvc.perform(get(topic)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.partitions[0].replicas", hasSize(2)));
        });
        mvc.perform(post("/api/topics").contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isConflict());

        mvc.perform(patch(topic).contentType(MediaType.APPLICATION_JSON).content("{\"partitions\":2}"))
                .andExpect(status().isNoContent());
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> mvc.perform(get(topic))
                .andExpect(jsonPath("$.partitions", hasSize(2))));
        mvc.perform(patch(topic).contentType(MediaType.APPLICATION_JSON).content("{\"partitions\":1}"))
                .andExpect(status().isBadRequest());

        mvc.perform(patch(topic + "/configs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"retention.ms\":\"120000\",\"segment.ms\":\"3600000\"}"))
                .andExpect(status().isNoContent());
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> mvc.perform(get(topic + "/configs"))
                .andExpect(jsonPath("$[?(@.name == 'retention.ms')].value", hasItem("120000"))));
        mvc.perform(patch(topic + "/configs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"retention.ms\":null}"))
                .andExpect(status().isNoContent());
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> mvc.perform(get(topic + "/configs"))
                .andExpect(jsonPath("$[?(@.name == 'retention.ms')].source", hasItem("DEFAULT_CONFIG")))
                .andExpect(jsonPath("$[?(@.name == 'segment.ms')].source", hasItem("DYNAMIC_TOPIC_CONFIG"))));

        mvc.perform(delete(topic)).andExpect(status().isNoContent());
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> mvc.perform(get(topic))
                .andExpect(status().isNotFound()));
    }

    @Test
    void managesDynamicBrokerConfigOverrides() throws Exception {
        mvc.perform(get("/api/brokers")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
        String configs = "/api/brokers/0/configs";
        mvc.perform(patch(configs).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"max.connections\":\"10000\"}"))
                .andExpect(status().isNoContent());
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> mvc.perform(get(configs))
                .andExpect(jsonPath("$[?(@.name == 'max.connections')].value", hasItem("10000"))));
        mvc.perform(patch(configs).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"max.connections\":null}"))
                .andExpect(status().isNoContent());
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> mvc.perform(get(configs))
                .andExpect(jsonPath("$[?(@.name == 'max.connections')].source", hasItem("DEFAULT_CONFIG"))));
        mvc.perform(get("/api/brokers/99/configs")).andExpect(status().isNotFound());
    }
}
