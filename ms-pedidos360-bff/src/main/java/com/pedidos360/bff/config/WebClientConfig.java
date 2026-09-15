package com.pedidos360.bff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${app.services.orders.url}")
    private String ordersServiceUrl;

    @Value("${app.services.catalog.url}")
    private String catalogServiceUrl;

    @Bean(name = "webClientOrders")
    public WebClient webClientOrders(WebClient.Builder builder) {
        return builder.baseUrl(ordersServiceUrl).build();
    }

    @Bean(name = "webClientCatalog")
    public WebClient webClientCatalog(WebClient.Builder builder) {
        return builder.baseUrl(catalogServiceUrl).build();
    }
}