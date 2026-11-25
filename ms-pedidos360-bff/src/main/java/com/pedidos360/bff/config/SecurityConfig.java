package com.pedidos360.bff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Configuración de seguridad para el BFF (stack reactivo WebFlux).
 * Valida el JWT de Azure AD (aud = nuestra API, issuer del tenant del token) antes de reenviar
 * las peticiones. La autorización fina por rol (Admin / Customer) la aplica cada microservicio.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Value("${app.security.api-client-id:}")
    private String apiClientId;

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private String allowedOrigins;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(Customizer.withDefaults())
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers("/actuator/health", "/actuator/info").permitAll()
                        // Catálogo público (catalog decide qué GET exige sesión). Pedidos y seguimiento exigen login.
                        .pathMatchers(HttpMethod.GET, "/api/catalog", "/api/catalog/**").permitAll()
                        // Retorno y confirmación de la pasarela de pago (Flow): sin JWT, payments verifica el token en Flow.
                        .pathMatchers("/api/payments/*/return", "/api/payments/*/confirmation").permitAll()
                        .pathMatchers("/api/catalog", "/api/catalog/**", "/api/orders", "/api/orders/**",
                                "/api/payments", "/api/payments/**").authenticated()
                        .anyExchange().denyAll()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(AzureAdJwtSupport.JWK_SET_URI).build();
        decoder.setJwtValidator(AzureAdJwtSupport.tokenValidator(apiClientId));
        return decoder;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setMaxAge(3600L);

        // La pasarela devuelve al comprador con un formulario POST desde su dominio (Origin: https://sandbox.flow.cl).
        // Es una navegación, no una llamada XHR: se acepta cualquier origen sin credenciales en esas dos rutas.
        CorsConfiguration gatewayCallbacks = new CorsConfiguration();
        gatewayCallbacks.setAllowedOriginPatterns(List.of("*"));
        gatewayCallbacks.setAllowedMethods(List.of("GET", "POST"));

        UrlBasedCorsConfigurationSource callbacks = new UrlBasedCorsConfigurationSource();
        callbacks.registerCorsConfiguration("/api/payments/*/return", gatewayCallbacks);
        callbacks.registerCorsConfiguration("/api/payments/*/confirmation", gatewayCallbacks);
        UrlBasedCorsConfigurationSource api = new UrlBasedCorsConfigurationSource();
        api.registerCorsConfiguration("/api/**", config);

        return exchange -> {
            CorsConfiguration callback = callbacks.getCorsConfiguration(exchange);
            return callback != null ? callback : api.getCorsConfiguration(exchange);
        };
    }
}
