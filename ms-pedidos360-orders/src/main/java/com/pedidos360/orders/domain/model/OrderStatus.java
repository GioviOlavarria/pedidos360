package com.pedidos360.orders.domain.model;

/**
 * Ciclo de vida del pedido:
 * PENDIENTE_PAGO → (pago confirmado) CREADO → ACEPTADO → EN_PREPARACION → DESPACHADO → ENTREGADO.
 * CANCELADO desde cualquier estado anterior a ENTREGADO (también si el pago se rechaza o vence).
 */
public enum OrderStatus {
    PENDIENTE_PAGO,
    CREADO,
    ACEPTADO,
    EN_PREPARACION,
    DESPACHADO,
    ENTREGADO,
    CANCELADO
}
