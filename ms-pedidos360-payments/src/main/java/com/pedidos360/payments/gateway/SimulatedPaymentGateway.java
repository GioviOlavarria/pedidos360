package com.pedidos360.payments.gateway;

import com.pedidos360.payments.config.PaymentProperties;
import com.pedidos360.payments.domain.Payment;
import com.pedidos360.payments.domain.PaymentStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Pasarela de prueba que imita a Flow sin cuenta ni internet: redirige a /checkout/simulado del frontend,
 * donde el comprador aprueba o rechaza el pago. Luego el flujo es idéntico al de Flow (status → Kafka).
 * Se activa con app.payments.provider=simulado (valor por defecto).
 */
@Component
@ConditionalOnProperty(name = "app.payments.provider", havingValue = "simulado", matchIfMissing = true)
public class SimulatedPaymentGateway implements PaymentGateway {

    private record Pending(BigDecimal amount, String reference, PaymentStatus status, String method) {
    }

    private final Map<String, Pending> payments = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(100_000);
    private final String frontendUrl;

    public SimulatedPaymentGateway(PaymentProperties properties) {
        this.frontendUrl = properties.frontendUrl();
    }

    @Override
    public String name() {
        return "SIMULADO";
    }

    @Override
    public Checkout create(Payment payment, String subject, String urlReturn, String urlConfirmation) {
        String token = UUID.randomUUID().toString();
        String reference = String.valueOf(sequence.incrementAndGet());
        payments.put(token, new Pending(payment.getAmount(), reference, PaymentStatus.PENDING, null));
        return new Checkout(token, reference, frontendUrl + "/checkout/simulado?token=" + token);
    }

    @Override
    public Result status(String token) {
        Pending pending = payments.get(token);
        if (pending == null) {
            throw new PaymentGatewayException("Token de pago simulado desconocido");
        }
        return new Result(pending.status(), pending.reference(), pending.amount(), pending.method());
    }

    /** El comprador aprueba (tarjeta de prueba) o rechaza el pago en la pantalla simulada. */
    public void resolve(String token, boolean approved) {
        payments.computeIfPresent(token, (key, pending) -> pending.status().isFinal() ? pending
                : new Pending(pending.amount(), pending.reference(),
                approved ? PaymentStatus.PAID : PaymentStatus.REJECTED, approved ? "Tarjeta de prueba" : null));
    }
}
