package com.pedidos360.rabbitadmin;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sin JWT de Entra ID o sin rol Admin no se puede administrar RabbitMQ. */
@WebMvcTest(controllers = RabbitAdminController.class, properties = AdminJwtTestConfig.API_CLIENT_ID)
@Import(SecurityConfig.class)
class SecurityTests {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AmqpAdmin admin;

    @MockBean
    private RestClient management;

    @Test
    void withoutTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/api/queues")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/queues/pedidos360.notifications.boleta")).andExpect(status().isUnauthorized());
        verifyNoInteractions(admin);
    }

    @Test
    void customerTokenIsForbidden() throws Exception {
        mvc.perform(post("/api/exchanges").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Customer")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\",\"type\":\"topic\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(admin);
    }
}
