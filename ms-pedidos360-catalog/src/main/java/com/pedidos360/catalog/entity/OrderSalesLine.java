package com.pedidos360.catalog.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Línea de venta de un pedido vigente, proyectada desde el tópico Kafka de eventos de pedidos.
 * Cada evento reemplaza las líneas de su pedido, por lo que reprocesar un evento no duplica ventas.
 */
@Entity
@Table(name = "order_sales_lines", indexes = {
        @Index(name = "idx_sales_order", columnList = "order_id"),
        @Index(name = "idx_sales_product", columnList = "product_id")
})
@Data
@NoArgsConstructor
public class OrderSalesLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name")
    private String productName;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    public OrderSalesLine(Long orderId, Long productId, String productName, Integer quantity, BigDecimal subtotal) {
        this.orderId = orderId;
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.subtotal = subtotal;
    }
}
