package com.pedidos360.bff;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.security.api-client-id=00000000-0000-0000-0000-000000000001",
        // Puerto cerrado: simula que el microservicio está caído.
        "app.services.orders.url=http://localhost:1",
        "app.services.catalog.url=http://localhost:1",
        "app.services.payments.url=http://localhost:1"
})
@AutoConfigureWebTestClient
class MsPedidos360BffApplicationTests {

    @Autowired
    private WebTestClient client;

    @Test
    void buyingRequiresLogin() {
        client.post().uri("/api/orders").bodyValue("{}").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void unknownPathsAreDenied() {
        client.mutateWith(mockJwt()).get().uri("/internal/stock/reserve").exchange().expectStatus().isForbidden();
    }

    @Test
    void paymentsRequireLoginExceptGatewayCallbacks() {
        client.post().uri("/api/payments").bodyValue("{}").exchange().expectStatus().isUnauthorized();
        // El retorno de Flow llega sin JWT: el BFF lo deja pasar (aquí payments está caído → 503, no 401).
        client.post().uri("/api/payments/flow/return?token=x").exchange().expectStatus().isEqualTo(503);
        // Flow envía el token como formulario: no debe responder 415.
        client.post().uri("/api/payments/flow/return")
                .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue("token=abc").exchange().expectStatus().isEqualTo(503);
        // El navegador envía Origin: https://sandbox.flow.cl en ese POST: el CORS no debe responder 403.
        client.post().uri("http://localhost/api/payments/flow/return")
                .header("Origin", "https://sandbox.flow.cl")
                .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue("token=abc").exchange().expectStatus().isEqualTo(503);
        // El resto de la API sigue limitado al origen del frontend.
        client.get().uri("http://localhost/api/catalog").header("Origin", "https://sandbox.flow.cl")
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void downstreamFailureReturns503() {
        client.get().uri("/api/catalog").exchange().expectStatus().isEqualTo(503);
    }
}
