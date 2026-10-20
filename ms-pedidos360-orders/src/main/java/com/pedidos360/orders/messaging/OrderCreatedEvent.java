package com.pedidos360.orders.messaging;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Mensaje publicado en RabbitMQ cuando se crea un pedido. Lo consume ms-pedidos360-notifications
 * para enviar la boleta por correo. Debe mantenerse compatible con la copia de notifications.
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
