package com.pedidos360.payments.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

/**
 * Consulta a ms-pedidos360-orders (endpoint interno con X-Internal-Api-Key) el monto, dueño y estado del pedido.
 * El monto a cobrar sale siempre del servidor, nunca del navegador.
 */
@Component
public class OrdersClient {

    public record OrderInfo(Long id, String status, BigDecimal total, String customerEmail, String customerName,
                            int itemCount) {
    }

    private final RestClient http;

    public OrdersClient(RestClient.Builder builder,
                        @Value("${app.services.orders.url}") String ordersUrl,
                        @Value("${app.internal.api-key:}") String internalApiKey) {
        this.http = builder.baseUrl(ordersUrl).defaultHeader("X-Internal-Api-Key", internalApiKey).build();
    }

    public OrderInfo getOrder(Long orderId) {
        try {
            return http.get().uri("/internal/orders/{id}", orderId).retrieve()
                    .onStatus(status -> status.value() == 404, (request, response) -> {
                        throw new ResponseStatusException(HttpStatusCode.valueOf(404), "Pedido no encontrado: " + orderId);
                    })
                    .body(OrderInfo.class);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(503), "El servicio de pedidos no está disponible", e);
        }
    }
}
