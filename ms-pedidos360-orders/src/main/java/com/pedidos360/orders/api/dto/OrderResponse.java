package com.pedidos360.orders.api.dto;

import com.pedidos360.orders.domain.model.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class OrderResponse {

    private Long id;
    private Long customerId;
    private OrderStatus status;
    private LocalDateTime createdAt;
    private BigDecimal total;
    private java.util.List<OrderItemResponse> items;

    public OrderResponse() {
    }

    public OrderResponse(Long id, Long customerId, OrderStatus status, LocalDateTime createdAt, BigDecimal total) {
        this.id = id;
        this.customerId = customerId;
        this.status = status;
        this.createdAt = createdAt;
        this.total = total;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public java.util.List<OrderItemResponse> getItems() {
        return items;
    }

    public void setItems(java.util.List<OrderItemResponse> items) {
        this.items = items;
    }
}