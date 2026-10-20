package com.pedidos360.orders.domain.model.exception;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(Long id) {
        super("Pedido no encontrado: " + id);
    }
}
