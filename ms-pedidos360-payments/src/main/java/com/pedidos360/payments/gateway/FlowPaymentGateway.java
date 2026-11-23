package com.pedidos360.payments.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.pedidos360.payments.config.PaymentProperties;
import com.pedidos360.payments.domain.Payment;
import com.pedidos360.payments.domain.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Integración con la API REST de Flow (https://developers.flow.cl/api).
 *  - POST /payment/create: crea la orden y devuelve url + token; el comprador paga en url?token=...
 *  - GET  /payment/getStatus: estado de la orden (1 pendiente, 2 pagada, 3 rechazada, 4 anulada).
 * Todas las peticiones se firman con {@link FlowSigner}.
 */
@Component
@ConditionalOnProperty(name = "app.payments.provider", havingValue = "flow")
public class FlowPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(FlowPaymentGateway.class);

    private final RestClient http;
    private final PaymentProperties.Flow flow;

    public FlowPaymentGateway(RestClient.Builder builder, PaymentProperties properties) {
        this.flow = properties.flow();
        if (flow == null || isBlank(flow.apiKey()) || isBlank(flow.secretKey())) {
            throw new IllegalStateException("Falta configurar FLOW_API_KEY y FLOW_SECRET_KEY (app.payments.flow)");
        }
        // Los timeouts los aplica HttpClientConfig (RestClientCustomizer) al builder que inyecta Spring.
        this.http = builder.baseUrl(flow.apiUrl()).build();
    }

    @Override
    public String name() {
        return "FLOW";
    }

    @Override
    public Checkout create(Payment payment, String subject, String urlReturn, String urlConfirmation) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("apiKey", flow.apiKey());
        params.put("commerceOrder", "P360-" + payment.getOrderId() + "-" + payment.getId());
        params.put("subject", subject);
        params.put("currency", payment.getCurrency());
        params.put("amount", payment.getAmount().toBigInteger().toString());
        params.put("email", payment.getPayerEmail());
        params.put("paymentMethod", "9"); // todos los medios de pago habilitados en el comercio
        params.put("urlConfirmation", urlConfirmation);
        params.put("urlReturn", urlReturn);
        params.put("optional", "{\"orderId\":\"" + payment.getOrderId() + "\"}");
        if (flow.timeout() != null) {
            params.put("timeout", String.valueOf(flow.timeout().toSeconds()));
        }
        params.put("s", FlowSigner.sign(params, flow.secretKey()));

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        params.forEach(form::add);
        JsonNode response = call(() -> http.post().uri("/payment/create")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class));

        String token = response.path("token").asText();
        String redirect = UriComponentsBuilder.fromHttpUrl(response.path("url").asText())
                .queryParam("token", token).build().toUriString();
        log.info("Flow: orden {} creada para el pedido #{} (pago {})", response.path("flowOrder").asText(),
                payment.getOrderId(), payment.getId());
        return new Checkout(token, response.path("flowOrder").asText(), redirect);
    }

    @Override
    public Result status(String token) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("apiKey", flow.apiKey());
        params.put("token", token);
        String signature = FlowSigner.sign(params, flow.secretKey());

        JsonNode response = call(() -> http.get()
                .uri(uri -> uri.path("/payment/getStatus")
                        .queryParam("apiKey", flow.apiKey())
                        .queryParam("token", token)
                        .queryParam("s", signature)
                        .build())
                .retrieve()
                .body(JsonNode.class));

        PaymentStatus status = switch (response.path("status").asInt()) {
            case 2 -> PaymentStatus.PAID;
            case 3 -> PaymentStatus.REJECTED;
            case 4 -> PaymentStatus.CANCELLED;
            default -> PaymentStatus.PENDING;
        };
        String method = response.path("paymentData").path("media").asText(null);
        return new Result(status, response.path("flowOrder").asText(null),
                new BigDecimal(response.path("amount").asText("0")), method);
    }

    private static JsonNode call(java.util.function.Supplier<JsonNode> request) {
        try {
            JsonNode body = request.get();
            if (body == null) {
                throw new PaymentGatewayException("Flow no devolvió respuesta");
            }
            return body;
        } catch (RestClientResponseException e) {
            // Flow responde {"code":..., "message":"..."} en los errores 400/401.
            throw new PaymentGatewayException("Flow rechazó la operación (" + e.getStatusCode().value() + "): "
                    + e.getResponseBodyAsString(), e);
        } catch (org.springframework.web.client.RestClientException e) {
            throw new PaymentGatewayException("No se pudo contactar a Flow", e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
