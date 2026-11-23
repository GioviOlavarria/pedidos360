package com.pedidos360.kafkaadmin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.GroupIdNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Administración de consumer groups: listar, inspeccionar miembros/asignaciones/lag, reiniciar offsets
 * (para reprocesar un tópico) y eliminar grupos que ya no se usan.
 */
@RestController
@RequestMapping("/api/consumer-groups")
public class ConsumerGroupController {

    private final Admin admin;

    public ConsumerGroupController(Admin admin) {
        this.admin = admin;
    }

    @GetMapping
    public List<GroupSummary> listGroups() {
        return await(admin.listConsumerGroups().all()).stream()
                .map(group -> new GroupSummary(group.groupId(),
                        group.state().map(Enum::name).orElse("UNKNOWN")))
                .sorted(Comparator.comparing(GroupSummary::groupId))
                .toList();
    }

    @GetMapping("/{groupId}")
    public GroupDetails describeGroup(@PathVariable String groupId) {
        ConsumerGroupDescription description = await(admin.describeConsumerGroups(List.of(groupId)).all()).get(groupId);
        if (description.state().name().equals("DEAD")) {
            throw new GroupIdNotFoundException("El grupo no existe: " + groupId);
        }
        List<MemberDetails> members = description.members().stream()
                .map(member -> new MemberDetails(member.consumerId(), member.clientId(), member.host(),
                        member.assignment().topicPartitions().stream()
                                .sorted(Comparator.comparing(TopicPartition::topic).thenComparingInt(TopicPartition::partition))
                                .map(TopicPartition::toString)
                                .toList()))
                .toList();

        Map<TopicPartition, OffsetAndMetadata> committed =
                await(admin.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata());
        Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> ends = committed.isEmpty() ? Map.of()
                : await(admin.listOffsets(committed.keySet().stream()
                .collect(Collectors.toMap(Function.identity(), tp -> OffsetSpec.latest()))).all());

        List<PartitionLag> offsets = committed.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .map(entry -> {
                    long current = entry.getValue().offset();
                    long end = ends.get(entry.getKey()).offset();
                    return new PartitionLag(entry.getKey().topic(), entry.getKey().partition(), current, end, end - current);
                })
                .sorted(Comparator.comparing(PartitionLag::topic).thenComparingInt(PartitionLag::partition))
                .toList();

        return new GroupDetails(description.groupId(), description.state().name(),
                description.partitionAssignor(), description.coordinator() == null ? null : description.coordinator().id(),
                members, offsets, offsets.stream().mapToLong(PartitionLag::lag).sum());
    }

    /**
     * Reinicia los offsets de un grupo sobre un tópico. El grupo debe estar vacío (consumidores detenidos);
     * si no, Kafka responde UnknownMemberId/NotEmpty y se devuelve 409.
     */
    @PostMapping("/{groupId}/reset-offsets")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetOffsets(@PathVariable String groupId, @Valid @RequestBody ResetOffsetsRequest request) {
        List<TopicPartition> partitions = await(admin.describeTopics(List.of(request.topic())).allTopicNames())
                .get(request.topic()).partitions().stream()
                .map(partition -> new TopicPartition(request.topic(), partition.partition()))
                .toList();
        OffsetSpec spec = request.to().equals("earliest") ? OffsetSpec.earliest() : OffsetSpec.latest();
        Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> targets = await(admin.listOffsets(
                partitions.stream().collect(Collectors.toMap(Function.identity(), tp -> spec))).all());
        Map<TopicPartition, OffsetAndMetadata> newOffsets = targets.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> new OffsetAndMetadata(entry.getValue().offset())));
        await(admin.alterConsumerGroupOffsets(groupId, newOffsets).all());
    }

    @DeleteMapping("/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGroup(@PathVariable String groupId) {
        await(admin.deleteConsumerGroups(List.of(groupId)).all());
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

    public record GroupSummary(String groupId, String state) {
    }

    public record GroupDetails(String groupId, String state, String assignor, Integer coordinator,
                               List<MemberDetails> members, List<PartitionLag> offsets, long totalLag) {
    }

    public record MemberDetails(String consumerId, String clientId, String host, List<String> assignments) {
    }

    public record PartitionLag(String topic, int partition, long committedOffset, long endOffset, long lag) {
    }

    public record ResetOffsetsRequest(@NotBlank String topic,
                                      @NotBlank @Pattern(regexp = "earliest|latest") String to) {
    }
}
