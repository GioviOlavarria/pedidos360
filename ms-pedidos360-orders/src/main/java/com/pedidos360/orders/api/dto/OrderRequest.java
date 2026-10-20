package com.pedidos360.orders.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public class OrderRequest {

    @NotNull(message = "customerId no puede ser nulo")
    @Positive(message = "customerId debe ser positivo")
    private Long customerId;

    /** Informativo: el total real se calcula en el servidor con los precios del catálogo. */
    private BigDecimal total;

    @NotEmpty(message = "el pedido debe tener al menos un producto")
    private java.util.List<@Valid OrderItemRequest> items;

    public OrderRequest() {
    }

    public OrderRequest(Long customerId, BigDecimal total) {
        this.customerId = customerId;
        this.total = total;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public java.util.List<OrderItemRequest> getItems() {
        return items;
    }

    public void setItems(java.util.List<OrderItemRequest> items) {
        this.items = items;
    }
}