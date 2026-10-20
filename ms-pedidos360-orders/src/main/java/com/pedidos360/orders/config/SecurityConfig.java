package com.pedidos360.orders.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuración de seguridad para ms-pedidos360-orders.
 *  - GET /api/orders/{id}: público (seguimiento de envíos por número de pedido).
 *  - GET /api/orders: autenticado (Admin ve todos, Customer solo los suyos).
 *  - POST /api/orders: autenticado (compra).
 *  - PATCH /api/orders/{id}/status: Admin u Operator.
 *  - PUT / DELETE: Admin.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.security.api-client-id:}")
    private String apiClientId;

    @Value("${app.security.admin-emails:}")
    private String adminEmails;

    @Value("${app.security.admin-tenant-id:}")
    private String adminTenantId;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/orders/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/orders").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/orders").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/orders/*/status").hasAnyRole("Admin", "Operator")
                        .anyRequest().hasRole("Admin")
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                );
        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(AzureAdJwtSupport.JWK_SET_URI).build();
        decoder.setJwtValidator(AzureAdJwtSupport.tokenValidator(apiClientId));
        return decoder;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(
                AzureAdJwtSupport.authoritiesConverter(AzureAdJwtSupport.parseEmails(adminEmails), adminTenantId));
        return converter;
    }
}
