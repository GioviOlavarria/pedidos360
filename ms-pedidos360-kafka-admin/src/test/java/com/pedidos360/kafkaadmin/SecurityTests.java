package com.pedidos360.kafkaadmin;

import org.apache.kafka.clients.admin.Admin;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sin JWT de Entra ID o sin rol Admin no se puede administrar Kafka. */
@AutoConfigureMockMvc
@SpringBootTest(properties = AdminJwtTestConfig.API_CLIENT_ID)
class SecurityTests {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private Admin admin;

    @Test
    void withoutTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/api/topics")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/consumer-groups")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/topics/pedidos360.orders.events")).andExpect(status().isUnauthorized());
    }

    @Test
    void customerTokenIsForbidden() throws Exception {
        mvc.perform(post("/api/topics").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Customer")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().is2xxSuccessful());
    }
}
