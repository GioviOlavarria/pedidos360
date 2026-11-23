package com.pedidos360.catalog.kafka;

/** Payload que no cumple el contrato. Reintentarlo no lo arregla: el error handler lo envía directo al DLT. */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message) {
        super(message);
    }
}
