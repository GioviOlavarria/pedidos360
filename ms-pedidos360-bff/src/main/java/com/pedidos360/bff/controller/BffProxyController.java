package com.pedidos360.bff.controller;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@RestController
public class BffProxyController {

    private final WebClient catalogClient;
    private final WebClient ordersClient;

    public BffProxyController(
            @Qualifier("webClientCatalog") WebClient catalogClient,
            @Qualifier("webClientOrders") WebClient ordersClient) {
        this.catalogClient = catalogClient;
        this.ordersClient = ordersClient;
    }

    @RequestMapping(value = {"/api/catalog", "/api/catalog/**"}, method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE})
    public Mono<ResponseEntity<byte[]>> proxyCatalog(
            @RequestHeader HttpHeaders headers,
            HttpMethod method,
            @RequestBody(required = false) byte[] body,
            org.springframework.web.server.ServerWebExchange exchange) {

        String path = exchange.getRequest().getURI().getRawPath();
        String query = exchange.getRequest().getURI().getRawQuery();
        String uri = query != null ? path + "?" + query : path;

        String authHeader = headers.getFirst(HttpHeaders.AUTHORIZATION);

        WebClient.RequestBodySpec spec = catalogClient.method(method).uri(uri);
        if (authHeader != null) {
            spec.header(HttpHeaders.AUTHORIZATION, authHeader);
        }
        if (headers.getContentType() != null) {
            spec.header(HttpHeaders.CONTENT_TYPE, headers.getContentType().toString());
        }

        if (body != null) {
            return spec.bodyValue(body).retrieve().toEntity(byte[].class);
        }
        return spec.retrieve().toEntity(byte[].class);
    }

    @RequestMapping(value = {"/api/orders", "/api/orders/**"}, method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE})
    public Mono<ResponseEntity<byte[]>> proxyOrders(
            @RequestHeader HttpHeaders headers,
            HttpMethod method,
            @RequestBody(required = false) byte[] body,
            org.springframework.web.server.ServerWebExchange exchange) {

        String path = exchange.getRequest().getURI().getRawPath();
        String query = exchange.getRequest().getURI().getRawQuery();
        String uri = query != null ? path + "?" + query : path;

        String authHeader = headers.getFirst(HttpHeaders.AUTHORIZATION);

        WebClient.RequestBodySpec spec = ordersClient.method(method).uri(uri);
        if (authHeader != null) {
            spec.header(HttpHeaders.AUTHORIZATION, authHeader);
        }
        if (headers.getContentType() != null) {
            spec.header(HttpHeaders.CONTENT_TYPE, headers.getContentType().toString());
        }

        if (body != null) {
            return spec.bodyValue(body).retrieve().toEntity(byte[].class);
        }
        return spec.retrieve().toEntity(byte[].class);
    }
}
