package com.pedidos360.orders.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuración de seguridad para ms-pedidos360-orders.
 * Valida JWT emitidos por Azure AD. Extrae roles del claim "roles".
 * Roles válidos del sistema: Admin, Operator, Customer.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder())
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())
                        )
                );
        return http.build();
    }

    @Bean
    public org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder() {
        org.springframework.security.oauth2.jwt.NimbusJwtDecoder decoder =
                org.springframework.security.oauth2.jwt.NimbusJwtDecoder
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

    /**
     * Convierte el claim "roles" del JWT de Azure AD en GrantedAuthority con prefijo ROLE_.
     * Ejemplo: rol "Operator" → ROLE_Operator
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthoritiesClaimName("roles");
        grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        return converter;
    }
}
