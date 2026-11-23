package com.pedidos360.payments;

import com.pedidos360.payments.client.OrdersClient;
import com.pedidos360.payments.client.OrdersClient.OrderInfo;
import com.pedidos360.payments.domain.PaymentRepository;
import com.pedidos360.payments.messaging.PaymentResultMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Flujo completo de pago con la pasarela simulada (H2, orders y Kafka simulados). */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:payments;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.kafka.admin.auto-create=false",
        "app.security.api-client-id=00000000-0000-0000-0000-000000000001",
        "app.payments.provider=simulado",
        "app.payments.frontend-url=http://front.test",
        "app.payments.public-url=http://bff.test"
})
@AutoConfigureMockMvc
class PaymentApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PaymentRepository payments;

    @MockBean
    private OrdersClient orders;

    @MockBean
    private KafkaTemplate<String, Object> kafka;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        payments.deleteAll();
        when(orders.getOrder(15L)).thenReturn(new OrderInfo(15L, "PENDIENTE_PAGO", new BigDecimal("5980.00"),
                "cliente@outlook.com", "Cliente", 1));
        SendResult<String, Object> sent = mock(SendResult.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        when(kafka.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(sent));
    }

    @Test
    void anonymousCannotStartPayment() throws Exception {
        mvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":15}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyTheOwnerCanPay() throws Exception {
        mvc.perform(post("/api/payments").with(user("otro@outlook.com"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":15}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void orderMustBePendingPayment() throws Exception {
        when(orders.getOrder(16L)).thenReturn(new OrderInfo(16L, "CREADO", BigDecimal.TEN, "cliente@outlook.com", "C", 1));
        mvc.perform(post("/api/payments").with(user("cliente@outlook.com"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":16}"))
                .andExpect(status().isConflict());
    }

    @Test
    void approvedPaymentIsPublishedForOrders() throws Exception {
        String token = startCheckout();

        mvc.perform(post("/api/payments/simulado/" + token + "/resolve").param("approved", "true")
                        .with(user("cliente@outlook.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paymentMethod").value("Tarjeta de prueba"));

        ArgumentCaptor<Object> message = ArgumentCaptor.forClass(Object.class);
        verify(kafka).send(eq("pedidos360.payments.events"), eq("15"), message.capture());
        PaymentResultMessage result = (PaymentResultMessage) message.getValue();
        assertEquals("PAID", result.status());
        assertEquals(0, new BigDecimal("5980").compareTo(result.amount()));
        assertEquals(true, payments.findByToken(token).orElseThrow().isEventPublished());
    }

    @Test
    void returnFromGatewayVerifiesAndRedirectsToFrontend() throws Exception {
        String token = startCheckout();

        // Sin resolver todavía: sigue pendiente y no se publica nada.
        mvc.perform(post("/api/payments/simulado/return").param("token", token))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "http://front.test/checkout/resultado?orderId=15"));
        verify(kafka, never()).send(anyString(), anyString(), any());

        mvc.perform(post("/api/payments/simulado/" + token + "/resolve").param("approved", "false")
                        .with(user("cliente@outlook.com")))
                .andExpect(jsonPath("$.status").value("REJECTED"));
        mvc.perform(get("/api/payments/orders/15").with(user("cliente@outlook.com")))
                .andExpect(jsonPath("$[0].status").value("REJECTED"));
        mvc.perform(get("/api/payments/token/" + token).with(user("cliente@outlook.com")))
                .andExpect(jsonPath("$.orderId").value(15));
        mvc.perform(get("/api/payments/token/" + token).with(user("otro@outlook.com")))
                .andExpect(status().isForbidden());
    }

    private String startCheckout() throws Exception {
        String body = mvc.perform(post("/api/payments").with(user("cliente@outlook.com"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":15}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.amount").value(5980))
                .andExpect(jsonPath("$.redirectUrl", startsWith("http://front.test/checkout/simulado?token=")))
                .andReturn().getResponse().getContentAsString();
        return body.replaceAll(".*token=([0-9a-f-]+).*", "$1");
    }

    private static RequestPostProcessor user(String email) {
        return jwt().jwt(j -> j.claim("preferred_username", email))
                .authorities(new SimpleGrantedAuthority("ROLE_Customer"));
    }
}
