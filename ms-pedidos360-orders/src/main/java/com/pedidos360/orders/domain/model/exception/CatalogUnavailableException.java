package com.pedidos360.orders.domain.model.exception;

/** El microservicio de catálogo no respondió o rechazó la llamada interna. */
public class CatalogUnavailableException extends RuntimeException {

    public CatalogUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
