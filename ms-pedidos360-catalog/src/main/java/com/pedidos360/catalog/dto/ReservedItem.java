package com.pedidos360.catalog.dto;

import java.math.BigDecimal;

/** Resultado de una reserva: precio y nombre vigentes del producto al momento de la compra. */
public record ReservedItem(Long productId, String name, BigDecimal unitPrice, Integer quantity) {
}
