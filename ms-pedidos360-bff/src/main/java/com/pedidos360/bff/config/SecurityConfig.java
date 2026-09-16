package com.pedidos360.bff.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Configuración de seguridad para el BFF (stack reactivo WebFlux).
 * Valida el JWT de Azure AD antes de reenviar peticiones a los microservicios internos.
 * Roles soportados: Admin, Operator, Customer (definidos en Azure AD).
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(org.springframework.http.HttpMethod.OPTIONS).permitAll()
                        .pathMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtDecoder(reactiveJwtDecoder()))
                )
                .build();
    }

    @Bean
    public org.springframework.security.oauth2.jwt.ReactiveJwtDecoder reactiveJwtDecoder() {
        org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder decoder =
                org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder
                        .withJwkSetUri("https://login.microsoftonline.com/common/discovery/v2.0/keys")
                        .build();

        org.springframework.security.oauth2.core.OAuth2TokenValidator<org.springframework.security.oauth2.jwt.Jwt> validator =
                new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
                        new org.springframework.security.oauth2.jwt.JwtTimestampValidator(),
                        new org.springframework.security.oauth2.jwt.JwtClaimValidator<String>(
                                org.springframework.security.oauth2.jwt.JwtClaimNames.ISS,
                                iss -> iss != null && iss.startsWith("https://login.microsoftonline.com/")
                        )
                );
        decoder.setJwtValidator(validator);
        return decoder;
    }
}
