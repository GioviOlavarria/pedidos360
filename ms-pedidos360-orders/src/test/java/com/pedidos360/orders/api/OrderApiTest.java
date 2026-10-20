package com.pedidos360.orders.api;

import com.pedidos360.orders.client.CatalogClient;
import com.pedidos360.orders.client.CatalogClient.ReservedItem;
import com.pedidos360.orders.config.RabbitMQConfig;
import com.pedidos360.orders.messaging.OrderCreatedEvent;
import com.pedidos360.orders.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderApiTest {

    private static final String ORDER_JSON = """
            {"customerId":1234,"total":1,"items":[{"productId":7,"quantity":2,"unitPrice":1}]}""";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private OrderRepository repository;

    @MockBean
    private CatalogClient catalogClient;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        when(catalogClient.reserve(any()))
                .thenReturn(List.of(new ReservedItem(7L, "Caja", new BigDecimal("2990.00"), 2)));
    }

    @Test
    void anonymousCannotListOrders() throws Exception {
        mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousCannotBuy() throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(ORDER_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void trackingByIdIsPublic() throws Exception {
        mvc.perform(get("/api/orders/999")).andExpect(status().isNotFound());
    }

    @Test
    void customerBuysWithCatalogPriceAndBoletaIsPublished() throws Exception {
        mvc.perform(post("/api/orders").with(customer("cliente@outlook.com"))
                        .contentType(MediaType.APPLICATION_JSON).content(ORDER_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(5980.00))
                .andExpect(jsonPath("$.status").value("CREADO"))
                .andExpect(jsonPath("$.items[0].productName").value("Caja"));

        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.EXCHANGE),
                eq(RabbitMQConfig.ROUTING_KEY_ORDER_CREATED), any(OrderCreatedEvent.class));
    }

    @Test
    void orderWithoutItemsIsRejected() throws Exception {
        mvc.perform(post("/api/orders").with(customer("cliente@outlook.com"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"customerId\":1,\"items\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void customerSeesOnlyOwnOrdersAdminSeesAll() throws Exception {
        buy("a@outlook.com");
        buy("b@outlook.com");

        mvc.perform(get("/api/orders").with(customer("a@outlook.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/orders").with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void customerCannotChangeStatusOrDelete() throws Exception {
        mvc.perform(patch("/api/orders/1/status").param("status", "ACEPTADO").with(customer("a@outlook.com")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/orders/1").with(customer("a@outlook.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminChangesStatusAndInvalidTransitionIs400() throws Exception {
        long id = buy("a@outlook.com");

        mvc.perform(patch("/api/orders/" + id + "/status").param("status", "ACEPTADO").with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACEPTADO"));
        mvc.perform(patch("/api/orders/" + id + "/status").param("status", "ENTREGADO").with(admin()))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/orders/" + id + "/status").param("status", "NO_EXISTE").with(admin()))
                .andExpect(status().isBadRequest());
    }

    private long buy(String email) throws Exception {
        String body = mvc.perform(post("/api/orders").with(customer(email))
                        .contentType(MediaType.APPLICATION_JSON).content(ORDER_JSON))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*?\"id\":(\\d+).*", "$1"));
    }

    private static RequestPostProcessor customer(String email) {
        return jwt().jwt(j -> j.claim("preferred_username", email).claim("name", "Cliente"))
                .authorities(new SimpleGrantedAuthority("ROLE_Customer"));
    }

    private static RequestPostProcessor admin() {
        return jwt().jwt(j -> j.claim("preferred_username", "gi.olavarria@duocuc.cl"))
                .authorities(new SimpleGrantedAuthority("ROLE_Admin"), new SimpleGrantedAuthority("ROLE_Customer"));
    }
}
