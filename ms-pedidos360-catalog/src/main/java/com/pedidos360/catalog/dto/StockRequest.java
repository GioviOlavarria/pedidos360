package com.pedidos360.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record StockRequest(@NotEmpty(message = "items no puede estar vacío") List<@Valid StockItem> items) {
}
