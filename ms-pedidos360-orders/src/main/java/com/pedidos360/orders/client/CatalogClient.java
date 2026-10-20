package com.pedidos360.orders.client;

import com.pedidos360.orders.domain.model.exception.CatalogUnavailableException;
import com.pedidos360.orders.domain.model.exception.StockUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Cliente HTTP hacia ms-pedidos360-catalog (endpoints internos de stock).
 * El catálogo es la fuente de verdad de precios y stock: el precio que envía el navegador se ignora.
 */
@Component
public class CatalogClient {

    public record StockItem(Long productId, Integer quantity) {
    }

    public record ReservedItem(Long productId, String name, BigDecimal unitPrice, Integer quantity) {
    }

    private record StockRequest(List<StockItem> items) {
    }

    private final RestClient restClient;

    public CatalogClient(RestClient.Builder builder,
                         @Value("${app.services.catalog.url}") String catalogUrl,
                         @Value("${app.internal.api-key:}") String internalApiKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = builder
                .baseUrl(catalogUrl)
                .requestFactory(requestFactory)
                .defaultHeader("X-Internal-Api-Key", internalApiKey)
                .build();
    }

    public List<ReservedItem> reserve(List<StockItem> items) {
        try {
            List<ReservedItem> reserved = restClient.post()
                    .uri("/internal/stock/reserve")
                    .body(new StockRequest(items))
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<ReservedItem>>() {
                    });
            return reserved != null ? reserved : List.of();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 400 || e.getStatusCode().value() == 404) {
                throw new StockUnavailableException(messageOf(e));
            }
            throw new CatalogUnavailableException("El catálogo rechazó la reserva de stock (" + e.getStatusCode().value() + ")", e);
        } catch (RestClientException e) {
            throw new CatalogUnavailableException("No se pudo contactar al catálogo para reservar stock", e);
        }
    }

    public void release(List<StockItem> items) {
        if (items.isEmpty()) return;
        try {
            restClient.post()
                    .uri("/internal/stock/release")
                    .body(new StockRequest(items))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new CatalogUnavailableException("No se pudo contactar al catálogo para devolver stock", e);
        }
    }

    private static String messageOf(HttpClientErrorException e) {
        try {
            Map<?, ?> body = e.getResponseBodyAs(Map.class);
            if (body != null && body.get("message") != null) return body.get("message").toString();
        } catch (RuntimeException ignored) {
            // cuerpo no JSON
        }
        return "Producto no disponible";
    }
}
