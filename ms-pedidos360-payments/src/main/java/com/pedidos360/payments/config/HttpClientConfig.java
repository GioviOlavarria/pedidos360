package com.pedidos360.payments.config;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

/** Timeouts para las llamadas HTTP salientes (Flow y orders): una pasarela lenta no bloquea los hilos. */
@Configuration
public class HttpClientConfig {

    @Bean
    public RestClientCustomizer timeouts() {
        return builder -> {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(Duration.ofSeconds(5));
            requestFactory.setReadTimeout(Duration.ofSeconds(15));
            builder.requestFactory(requestFactory);
        };
    }
}
