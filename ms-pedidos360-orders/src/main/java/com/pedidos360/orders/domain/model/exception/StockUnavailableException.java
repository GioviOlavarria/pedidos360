package com.pedidos360.orders.domain.model.exception;

/** Producto inexistente o sin stock suficiente para el pedido. */
public class StockUnavailableException extends RuntimeException {

    public StockUnavailableException(String message) {
        super(message);
    }
}
