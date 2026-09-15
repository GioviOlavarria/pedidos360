package com.pedidos360.orders.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public class OrderItemRequest {
    @NotNull(message = "productId no puede ser nulo")
    private Long productId;

    @NotNull(message = "quantity no puede ser nulo")
    @Positive(message = "quantity debe ser positivo")
    private Integer quantity;

    @NotNull(message = "unitPrice no puede ser nulo")
    @Positive(message = "unitPrice debe ser positivo")
    private BigDecimal unitPrice;

    public OrderItemRequest() {}

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
}
