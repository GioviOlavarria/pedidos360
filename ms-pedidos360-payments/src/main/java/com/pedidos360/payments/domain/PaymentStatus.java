package com.pedidos360.payments.domain;

/** Estado de un intento de pago. Solo PENDING puede cambiar; el resto es final. */
public enum PaymentStatus {
    PENDING,
    PAID,
    REJECTED,
    CANCELLED,
    EXPIRED;

    public boolean isFinal() {
        return this != PENDING;
    }
}
