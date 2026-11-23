package com.pedidos360.payments.gateway;

import com.pedidos360.payments.domain.Payment;
import com.pedidos360.payments.domain.PaymentStatus;

/** Puerto hacia la pasarela de pago (Flow o la pasarela simulada). */
public interface PaymentGateway {

    /** Nombre que se guarda en el pago: FLOW o SIMULADO. */
    String name();

    /**
     * Crea la orden de pago en la pasarela.
     *
     * @param urlReturn       adonde la pasarela devuelve al comprador
     * @param urlConfirmation adonde la pasarela avisa servidor a servidor el resultado
     */
    Checkout create(Payment payment, String subject, String urlReturn, String urlConfirmation);

    /** Consulta el estado del pago en la pasarela (fuente de verdad, nunca se confía en el navegador). */
    Result status(String token);

    record Checkout(String token, String providerReference, String redirectUrl) {
    }

    record Result(PaymentStatus status, String providerReference, java.math.BigDecimal amount, String paymentMethod) {
    }
}
