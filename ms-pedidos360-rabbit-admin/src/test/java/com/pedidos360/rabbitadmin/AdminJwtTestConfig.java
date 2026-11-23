package com.pedidos360.rabbitadmin;

import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Para los tests funcionales: cada petición de MockMvc lleva un JWT con rol Admin.
 * La validación real del token (401/403) se prueba en SecurityTests.
 */
@TestConfiguration
public class AdminJwtTestConfig {

    public static final String API_CLIENT_ID = "app.security.api-client-id=00000000-0000-0000-0000-000000000001";

    @Bean
    MockMvcBuilderCustomizer adminJwtByDefault() {
        return builder -> builder.defaultRequest(get("/").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Admin"))));
    }
}
