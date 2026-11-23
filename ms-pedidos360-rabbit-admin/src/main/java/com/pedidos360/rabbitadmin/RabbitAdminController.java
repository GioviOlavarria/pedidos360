package com.pedidos360.rabbitadmin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.Map;

/**
 * Administración de RabbitMQ: crea y elimina colas, exchanges y bindings (AMQP, vía RabbitAdmin)
 * y los lista desde la API HTTP del plugin management. Opera sobre el vhost por defecto "/".
 */
@RestController
@RequestMapping("/api")
public class RabbitAdminController {

    private static final String NAME = "[A-Za-z0-9._:-]+";
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() {
    };

    private final AmqpAdmin admin;
    private final RestClient management;

    public RabbitAdminController(AmqpAdmin admin, RestClient management) {
        this.admin = admin;
        this.management = management;
    }

    // ── Colas ────────────────────────────────────────────────────────────────

    @GetMapping("/queues")
    public List<Map<String, Object>> listQueues() {
        return list("/api/queues/%2F", "name", "type", "durable", "messages", "consumers", "arguments");
    }

    /** Crea una cola. type=quorum la replica en el clúster; deadLetterExchange la conecta a una DLQ. */
    @PostMapping("/queues")
    public ResponseEntity<Void> createQueue(@Valid @RequestBody QueueRequest request) {
        QueueBuilder builder = QueueBuilder.durable(request.name());
        if ("quorum".equals(request.type())) {
            builder.quorum();
        }
        if (request.deadLetterExchange() != null) {
            builder.deadLetterExchange(request.deadLetterExchange());
            if (request.deadLetterRoutingKey() != null) {
                builder.deadLetterRoutingKey(request.deadLetterRoutingKey());
            }
        }
        Queue queue = builder.build();
        admin.declareQueue(queue);
        return created(request.name());
    }

    @DeleteMapping("/queues/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteQueue(@PathVariable String name) {
        admin.deleteQueue(name);
    }

    @DeleteMapping("/queues/{name}/messages")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void purgeQueue(@PathVariable String name) {
        admin.purgeQueue(name, false);
    }

    // ── Exchanges ────────────────────────────────────────────────────────────

    @GetMapping("/exchanges")
    public List<Map<String, Object>> listExchanges() {
        return list("/api/exchanges/%2F", "name", "type", "durable", "auto_delete").stream()
                .filter(exchange -> !String.valueOf(exchange.get("name")).isEmpty())
                .toList();
    }

    @PostMapping("/exchanges")
    public ResponseEntity<Void> createExchange(@Valid @RequestBody ExchangeRequest request) {
        Exchange exchange = new ExchangeBuilder(request.name(), request.type()).durable(true).build();
        admin.declareExchange(exchange);
        return created(request.name());
    }

    @DeleteMapping("/exchanges/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExchange(@PathVariable String name) {
        admin.deleteExchange(name);
    }

    // ── Bindings ─────────────────────────────────────────────────────────────

    @GetMapping("/bindings")
    public List<Map<String, Object>> listBindings() {
        return list("/api/bindings/%2F", "source", "destination", "destination_type", "routing_key").stream()
                .filter(binding -> !String.valueOf(binding.get("source")).isEmpty())
                .toList();
    }

    @PostMapping("/bindings")
    @ResponseStatus(HttpStatus.CREATED)
    public void createBinding(@Valid @RequestBody BindingRequest request) {
        admin.declareBinding(toBinding(request));
    }

    /** Elimina un binding (mismos datos con que se creó). */
    @DeleteMapping("/bindings")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBinding(@RequestParam String exchange, @RequestParam String queue,
                              @RequestParam(defaultValue = "") String routingKey) {
        admin.removeBinding(toBinding(new BindingRequest(exchange, queue, routingKey)));
    }

    private static Binding toBinding(BindingRequest request) {
        return new Binding(request.queue(), Binding.DestinationType.QUEUE, request.exchange(),
                request.routingKey() == null ? "" : request.routingKey(), null);
    }

    private List<Map<String, Object>> list(String path, String... fields) {
        List<Map<String, Object>> items = management.get().uri(path).retrieve().body(LIST);
        if (items == null) {
            return List.of();
        }
        List<String> keep = List.of(fields);
        return items.stream()
                .map(item -> {
                    Map<String, Object> filtered = new java.util.LinkedHashMap<>();
                    keep.forEach(key -> filtered.put(key, item.get(key)));
                    return filtered;
                })
                .toList();
    }

    private static ResponseEntity<Void> created(String name) {
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().pathSegment(name).build().toUri())
                .build();
    }

    public record QueueRequest(@NotBlank @Size(max = 255) @Pattern(regexp = NAME) String name,
                               @Pattern(regexp = "classic|quorum") String type,
                               @Pattern(regexp = NAME) String deadLetterExchange,
                               String deadLetterRoutingKey) {
    }

    public record ExchangeRequest(@NotBlank @Size(max = 255) @Pattern(regexp = NAME) String name,
                                  @NotBlank @Pattern(regexp = "direct|topic|fanout|headers") String type) {
    }

    public record BindingRequest(@NotBlank @Pattern(regexp = NAME) String exchange,
                                 @NotBlank @Pattern(regexp = NAME) String queue,
                                 String routingKey) {
    }
}
