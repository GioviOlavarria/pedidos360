package com.pedidos360.orders.messaging;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Mensaje publicado en RabbitMQ cuando se confirma el pago de un pedido. Lo consume ms-pedidos360-notifications
 * para emitir la boleta electrónica (SII) y enviarla por correo. Compatible con OrderCreatedEvent de notifications.
 */
public record OrderPaidEvent(
        Long orderId,
        String customerEmail,
        String customerName,
        LocalDateTime createdAt,
        BigDecimal total,
        List<Item> items) {

    public record Item(Long productId, String productName, Integer quantity, BigDecimal unitPrice, BigDecimal subtotal) {
    }
}
