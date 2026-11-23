package com.pedidos360.payments.api;

import com.pedidos360.payments.config.AzureAdJwtSupport;
import com.pedidos360.payments.config.PaymentProperties;
import com.pedidos360.payments.domain.Payment;
import com.pedidos360.payments.gateway.SimulatedPaymentGateway;
import com.pedidos360.payments.service.PaymentService;
import com.pedidos360.payments.service.PaymentService.Buyer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * API de pagos (a través del BFF en /api/payments).
 *  - POST /api/payments                          inicia el pago de un pedido → URL de la pasarela (JWT)
 *  - GET  /api/payments/orders/{orderId}         pagos del pedido (JWT, dueño o Admin)
 *  - POST /api/payments/orders/{orderId}/refresh vuelve a consultar los pagos pendientes en la pasarela (JWT)
 *  - GET  /api/payments/token/{token}            detalle de un pago por su token (JWT, dueño o Admin)
 *  - POST|GET /api/payments/{provider}/return    Flow devuelve al comprador: se verifica y se redirige al frontend
 *  - POST /api/payments/{provider}/confirmation  aviso servidor a servidor de Flow
 *  - POST /api/payments/simulado/{token}/resolve aprobar/rechazar en la pasarela simulada (JWT)
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    public record CheckoutRequest(@NotNull @Positive Long orderId) {
    }

    public record PaymentView(Long id, Long orderId, String status, BigDecimal amount, String provider,
                              String providerReference, String paymentMethod, String redirectUrl, Instant createdAt,
                              Instant completedAt) {
        static PaymentView of(Payment p) {
            return new PaymentView(p.getId(), p.getOrderId(), p.getStatus().name(), p.getAmount(), p.getProvider(),
                    p.getProviderReference(), p.getPaymentMethod(), p.getRedirectUrl(), p.getCreatedAt(),
                    p.getCompletedAt());
        }
    }

    private final PaymentService service;
    private final PaymentProperties properties;
    private final ObjectProvider<SimulatedPaymentGateway> simulated;

    public PaymentController(PaymentService service, PaymentProperties properties,
                             ObjectProvider<SimulatedPaymentGateway> simulated) {
        this.service = service;
        this.properties = properties;
        this.simulated = simulated;
    }

    @PostMapping
    public ResponseEntity<PaymentView> checkout(@Valid @RequestBody CheckoutRequest request,
                                                @AuthenticationPrincipal Jwt jwt) {
        Payment payment = service.startCheckout(request.orderId(), buyer(jwt));
        return ResponseEntity.status(HttpStatus.CREATED).body(PaymentView.of(payment));
    }

    @GetMapping("/orders/{orderId}")
    public List<PaymentView> forOrder(@PathVariable Long orderId, @AuthenticationPrincipal Jwt jwt) {
        return service.forOrder(orderId, buyer(jwt)).stream().map(PaymentView::of).toList();
    }

    /** Detalle de un pago por su token (pantalla de la pasarela simulada). Solo el dueño o un Admin. */
    @GetMapping("/token/{token}")
    public PaymentView byToken(@PathVariable String token, @AuthenticationPrincipal Jwt jwt) {
        return PaymentView.of(service.byToken(token, buyer(jwt)));
    }

    @PostMapping("/orders/{orderId}/refresh")
    public List<PaymentView> refresh(@PathVariable Long orderId, @AuthenticationPrincipal Jwt jwt) {
        return service.refresh(orderId, buyer(jwt)).stream().map(PaymentView::of).toList();
    }

    /** Flow redirige al comprador con un POST (token en el formulario); se acepta también GET. */
    @RequestMapping(value = "/{provider}/return", method = {RequestMethod.POST, RequestMethod.GET})
    public ResponseEntity<Void> returnFromGateway(@PathVariable String provider, @RequestParam String token) {
        Payment payment = service.confirm(token);
        URI target = URI.create(properties.frontendUrl() + "/checkout/resultado?orderId=" + payment.getOrderId());
        return ResponseEntity.status(HttpStatus.SEE_OTHER).header(HttpHeaders.LOCATION, target.toString()).build();
    }

    /** Confirmación servidor a servidor de Flow: debe responder 200 rápidamente. */
    @PostMapping("/{provider}/confirmation")
    public ResponseEntity<Void> confirmation(@PathVariable String provider, @RequestParam String token) {
        service.confirm(token);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/simulado/{token}/resolve")
    public PaymentView resolveSimulated(@PathVariable String token, @RequestParam boolean approved,
                                        @AuthenticationPrincipal Jwt jwt) {
        SimulatedPaymentGateway gateway = simulated.getIfAvailable();
        if (gateway == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La pasarela simulada no está activa");
        }
        service.byToken(token, buyer(jwt)); // solo el dueño del pedido
        gateway.resolve(token, approved);
        return PaymentView.of(service.confirm(token));
    }

    /** Comprador según el JWT: correo y si tiene rol Admin (lo asigna AzureAdJwtSupport). */
    private static Buyer buyer(Jwt jwt) {
        boolean admin = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_Admin"));
        return new Buyer(AzureAdJwtSupport.resolveEmail(jwt).orElse(null), admin);
    }
}
