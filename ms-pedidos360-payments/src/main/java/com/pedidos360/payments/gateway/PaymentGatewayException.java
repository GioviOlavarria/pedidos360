package com.pedidos360.payments.gateway;

/** La pasarela de pago no respondió o rechazó la operación. */
public class PaymentGatewayException extends RuntimeException {

    public PaymentGatewayException(String message) {
        super(message);
    }

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
