package com.pedidos360.notifications.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Evento publicado por ms-pedidos360-orders al crear un pedido.
 * Debe mantenerse compatible con com.pedidos360.orders.messaging.OrderCreatedEvent.
 */
public record OrderCreatedEvent(
        Long orderId,
        String customerEmail,
        String customerName,
        LocalDateTime createdAt,
        BigDecimal total,
        List<Item> items) {

    public record Item(Long productId, String productName, Integer quantity, BigDecimal unitPrice, BigDecimal subtotal) {
    }
}
