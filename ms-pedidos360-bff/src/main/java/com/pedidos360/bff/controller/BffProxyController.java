package com.pedidos360.bff.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Reenvía /api/catalog/** y /api/orders/** a los microservicios internos.
 * Propaga tal cual el código HTTP y el cuerpo de la respuesta (400, 404, 409...) para que el
 * frontend pueda mostrar el mensaje real del error.
 */
@RestController
public class BffProxyController {

    private static final Logger log = LoggerFactory.getLogger(BffProxyController.class);

    private final WebClient catalogClient;
    private final WebClient ordersClient;

    public BffProxyController(
            @Qualifier("webClientCatalog") WebClient catalogClient,
            @Qualifier("webClientOrders") WebClient ordersClient) {
        this.catalogClient = catalogClient;
        this.ordersClient = ordersClient;
    }

    @RequestMapping(value = {"/api/catalog", "/api/catalog/**"}, method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE})
    public Mono<ResponseEntity<byte[]>> proxyCatalog(@RequestHeader HttpHeaders headers, HttpMethod method,
                                                     @RequestBody(required = false) byte[] body,
                                                     ServerWebExchange exchange) {
        return forward(catalogClient, "catalog", headers, method, body, exchange);
    }

    @RequestMapping(value = {"/api/orders", "/api/orders/**"}, method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE})
    public Mono<ResponseEntity<byte[]>> proxyOrders(@RequestHeader HttpHeaders headers, HttpMethod method,
                                                    @RequestBody(required = false) byte[] body,
                                                    ServerWebExchange exchange) {
        return forward(ordersClient, "orders", headers, method, body, exchange);
    }

    private Mono<ResponseEntity<byte[]>> forward(WebClient client, String serviceName, HttpHeaders headers,
                                                 HttpMethod method, byte[] body, ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getRawPath();
        String query = exchange.getRequest().getURI().getRawQuery();
        String uri = query != null ? path + "?" + query : path;

        WebClient.RequestBodySpec spec = client.method(method).uri(uri);
        String authHeader = headers.getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null) {
            spec.header(HttpHeaders.AUTHORIZATION, authHeader);
        }
        if (headers.getContentType() != null) {
            spec.contentType(headers.getContentType());
        }

        WebClient.RequestHeadersSpec<?> request = body != null ? spec.bodyValue(body) : spec;
        return request
                .exchangeToMono(response -> response.bodyToMono(byte[].class)
                        .defaultIfEmpty(new byte[0])
                        .map(responseBody -> {
                            HttpHeaders out = new HttpHeaders();
                            response.headers().contentType().ifPresent(out::setContentType);
                            return ResponseEntity.status(response.statusCode()).headers(out).body(responseBody);
                        }))
                .onErrorResume(WebClientRequestException.class, e -> {
                    log.error("Servicio {} no disponible: {}", serviceName, e.getMessage());
                    String json = "{\"status\":503,\"message\":\"El servicio de " + serviceName + " no está disponible\"}";
                    return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(json.getBytes(StandardCharsets.UTF_8)));
                });
    }
}
