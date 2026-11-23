package com.pedidos360.payments;

import com.pedidos360.payments.config.PaymentProperties;
import com.pedidos360.payments.domain.Payment;
import com.pedidos360.payments.domain.PaymentStatus;
import com.pedidos360.payments.gateway.FlowPaymentGateway;
import com.pedidos360.payments.gateway.FlowSigner;
import com.pedidos360.payments.gateway.PaymentGateway;
import com.pedidos360.payments.gateway.PaymentGatewayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Integración con la API de Flow contra un servidor HTTP simulado (sin llamar a Flow de verdad). */
class FlowPaymentGatewayTest {

    private static final String SECRET = "secreto-de-prueba";

    private MockRestServiceServer flow;
    private FlowPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        flow = MockRestServiceServer.bindTo(builder).build();
        gateway = new FlowPaymentGateway(builder, new PaymentProperties("flow", "https://tienda.cl", "https://tienda.cl",
                new PaymentProperties.Flow("https://sandbox.flow.cl/api", "API-KEY", SECRET, Duration.ofMinutes(20))));
    }

    @Test
    void signsLikeFlowDocumentation() {
        // Valor calculado con HMAC-SHA256 independiente (Python hmac) sobre "amount5000apiKeyXXXX-XXXX-XXXXcurrencyCLP".
        Map<String, String> params = Map.of("currency", "CLP", "apiKey", "XXXX-XXXX-XXXX", "amount", "5000");
        assertEquals("23a2e1765aa31ff2e0e242d8eb5650ae3302d8089159c3a5c968faa14b3578c8", FlowSigner.sign(params, SECRET));
    }

    @Test
    void createsSignedPaymentAndBuildsRedirectUrl() {
        flow.expect(requestTo("https://sandbox.flow.cl/api/payment/create"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(request -> {
                    Map<String, String> form = parseForm(((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsString());
                    assertEquals("5980", form.get("amount"));
                    assertEquals("P360-15-3", form.get("commerceOrder"));
                    assertEquals("1200", form.get("timeout"));
                    Map<String, String> unsigned = new LinkedHashMap<>(form);
                    unsigned.remove("s");
                    assertEquals(FlowSigner.sign(unsigned, SECRET), form.get("s"));
                })
                .andRespond(withSuccess("""
                        {"url":"https://sandbox.flow.cl/app/web/pay.php","token":"TOKEN123","flowOrder":8765}
                        """, MediaType.APPLICATION_JSON));

        PaymentGateway.Checkout checkout = gateway.create(payment(), "Pedido #15",
                "https://tienda.cl/api/payments/flow/return", "https://tienda.cl/api/payments/flow/confirmation");

        assertEquals("TOKEN123", checkout.token());
        assertEquals("8765", checkout.providerReference());
        assertEquals("https://sandbox.flow.cl/app/web/pay.php?token=TOKEN123", checkout.redirectUrl());
        flow.verify();
    }

    @Test
    void mapsFlowStatusCodes() {
        expectStatus("TOK-PAID", 2);
        expectStatus("TOK-REJ", 3);
        expectStatus("TOK-NUL", 4);
        expectStatus("TOK-PEND", 1);

        PaymentGateway.Result paid = gateway.status("TOK-PAID");
        assertEquals(PaymentStatus.PAID, paid.status());
        assertEquals("Webpay", paid.paymentMethod());
        assertEquals(0, new BigDecimal("5980").compareTo(paid.amount()));
        assertEquals(PaymentStatus.REJECTED, gateway.status("TOK-REJ").status());
        assertEquals(PaymentStatus.CANCELLED, gateway.status("TOK-NUL").status());
        assertEquals(PaymentStatus.PENDING, gateway.status("TOK-PEND").status());
    }

    @Test
    void flowErrorsBecomeGatewayExceptions() {
        flow.expect(requestTo("https://sandbox.flow.cl/api/payment/create"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":108,\"message\":\"Invalid apiKey\"}"));

        assertThrows(PaymentGatewayException.class, () -> gateway.create(payment(), "x", "a", "b"));
    }

    private void expectStatus(String token, int status) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("apiKey", "API-KEY");
        params.put("token", token);
        flow.expect(requestToUriTemplate("https://sandbox.flow.cl/api/payment/getStatus?apiKey={k}&token={t}&s={s}",
                        "API-KEY", token, FlowSigner.sign(params, SECRET)))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("token", token))
                .andRespond(withSuccess("""
                        {"flowOrder":8765,"commerceOrder":"P360-15-3","status":%d,"amount":5980,
                         "paymentData":{"media":"Webpay"}}
                        """.formatted(status), MediaType.APPLICATION_JSON));
    }

    private static Payment payment() {
        Payment payment = new Payment(15L, new BigDecimal("5980"), "FLOW", "cliente@outlook.com");
        ReflectionTestUtils.setField(payment, "id", 3L);
        return payment;
    }

    private static Map<String, String> parseForm(String body) {
        return Arrays.stream(body.split("&")).map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(kv -> URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                        kv -> kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "",
                        (a, b) -> a, LinkedHashMap::new));
    }
}
