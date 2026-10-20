package com.pedidos360.catalog.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Producto y cantidad a reservar o liberar. */
public record StockItem(
        @NotNull(message = "productId no puede ser nulo") Long productId,
        @NotNull(message = "quantity no puede ser nulo") @Positive(message = "quantity debe ser positivo") Integer quantity) {
}
