package com.pedidos360.kafkaadmin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.NewPartitions;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.config.ConfigResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api")
public class KafkaAdminController {

    private final Admin admin;

    public KafkaAdminController(Admin admin) {
        this.admin = admin;
    }

    @GetMapping("/topics")
    public List<String> listTopics() {
        return await(admin.listTopics().names()).stream().sorted().toList();
    }

    @PostMapping("/topics")
    public ResponseEntity<Void> createTopic(@Valid @RequestBody CreateTopicRequest request) {
        NewTopic topic = new NewTopic(request.name(), Optional.ofNullable(request.partitions()),
                Optional.ofNullable(request.replicationFactor()));
        if (request.configs() != null) {
            topic.configs(request.configs());
        }
        await(admin.createTopics(List.of(topic)).all());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().pathSegment(request.name()).build().toUri();
        return ResponseEntity.created(location).build();
    }

    @GetMapping("/topics/{topic}")
    public TopicDetails describeTopic(@PathVariable String topic) {
        var description = await(admin.describeTopics(List.of(topic)).allTopicNames()).get(topic);
        List<PartitionDetails> partitions = description.partitions().stream()
                .map(partition -> new PartitionDetails(partition.partition(),
                        partition.leader() == null ? null : partition.leader().id(),
                        partition.replicas().stream().map(Node::id).toList(),
                        partition.isr().stream().map(Node::id).toList()))
                .toList();
        return new TopicDetails(description.name(), partitions);
    }

    @PatchMapping("/topics/{topic}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void increasePartitions(@PathVariable String topic, @Valid @RequestBody PartitionsRequest request) {
        await(admin.createPartitions(Map.of(topic, NewPartitions.increaseTo(request.partitions()))).all());
    }

    @DeleteMapping("/topics/{topic}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTopic(@PathVariable String topic) {
        await(admin.deleteTopics(List.of(topic)).all());
    }

    @GetMapping("/topics/{topic}/configs")
    public List<ConfigDetails> topicConfigs(@PathVariable String topic) {
        return describeConfigs(new ConfigResource(ConfigResource.Type.TOPIC, topic));
    }

    @PatchMapping("/topics/{topic}/configs")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeTopicConfigs(@PathVariable String topic, @RequestBody Map<String, String> changes) {
        alterConfigs(new ConfigResource(ConfigResource.Type.TOPIC, topic), changes);
    }

    @GetMapping("/brokers")
    public List<BrokerDetails> listBrokers() {
        return await(admin.describeCluster().nodes()).stream()
                .map(node -> new BrokerDetails(node.id(), node.host(), node.port()))
                .sorted(Comparator.comparingInt(BrokerDetails::id))
                .toList();
    }

    @GetMapping("/brokers/{id}/configs")
    public List<ConfigDetails> brokerConfigs(@PathVariable int id) {
        return describeConfigs(brokerResource(id));
    }

    @PatchMapping("/brokers/{id}/configs")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeBrokerConfigs(@PathVariable int id, @RequestBody Map<String, String> changes) {
        alterConfigs(brokerResource(id), changes);
    }

    private ConfigResource brokerResource(int id) {
        if (await(admin.describeCluster().nodes()).stream().noneMatch(node -> node.id() == id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Broker no disponible: " + id);
        }
        return new ConfigResource(ConfigResource.Type.BROKER, Integer.toString(id));
    }

    private List<ConfigDetails> describeConfigs(ConfigResource resource) {
        return await(admin.describeConfigs(List.of(resource)).all()).get(resource).entries().stream()
                .map(entry -> new ConfigDetails(entry.name(), entry.value(), entry.source(),
                        entry.isReadOnly(), entry.isSensitive()))
                .sorted(Comparator.comparing(ConfigDetails::name))
                .toList();
    }

    private void alterConfigs(ConfigResource resource, Map<String, String> changes) {
        if (changes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Incluye al menos una configuración");
        }
        List<AlterConfigOp> operations = changes.entrySet().stream()
                .map(entry -> new AlterConfigOp(new ConfigEntry(entry.getKey(), entry.getValue()),
                        entry.getValue() == null ? AlterConfigOp.OpType.DELETE : AlterConfigOp.OpType.SET))
                .toList();
        await(admin.incrementalAlterConfigs(Map.of(resource, operations)).all());
    }

    private static <T> T await(KafkaFuture<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Operación interrumpida", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof KafkaException kafkaError) {
                throw kafkaError;
            }
            throw new IllegalStateException("Falló la operación de Kafka", e.getCause());
        }
    }

    public record CreateTopicRequest(
            @NotBlank @Size(max = 249) @Pattern(regexp = "[A-Za-z0-9._-]+") String name,
            @Min(1) Integer partitions,
            @Min(1) Short replicationFactor,
            Map<@NotBlank String, @NotNull String> configs) {
    }

    public record PartitionsRequest(@NotNull @Min(1) Integer partitions) {
    }

    public record TopicDetails(String name, List<PartitionDetails> partitions) {
    }

    public record PartitionDetails(int id, Integer leader, List<Integer> replicas, List<Integer> inSyncReplicas) {
    }

    public record BrokerDetails(int id, String host, int port) {
    }

    public record ConfigDetails(String name, String value, ConfigEntry.ConfigSource source,
                                boolean readOnly, boolean sensitive) {
    }
}
