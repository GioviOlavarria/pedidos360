package com.pedidos360.catalog.dto;

import java.math.BigDecimal;

/** Ventas acumuladas de un producto en pedidos vigentes (no cancelados ni eliminados). */
public record ProductSalesResponse(Long productId, String productName, Long unitsSold, BigDecimal revenue, Long orders) {
}
