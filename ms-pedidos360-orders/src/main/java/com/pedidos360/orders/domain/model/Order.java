package com.pedidos360.orders.domain.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders", indexes = @Index(name = "idx_orders_customer_email", columnList = "customer_email"))
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR) // VARCHAR y no ENUM nativo: agregar estados no requiere migrar la columna
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @NotNull
    @PositiveOrZero
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    /** Correo del comprador (tomado del token de Azure AD), destino de la boleta. */
    @Column(name = "customer_email", length = 320)
    private String customerEmail;

    @Column(name = "customer_name")
    private String customerName;

    /** Pago confirmado por ms-pedidos360-payments (Kafka) o registrado a mano por el Admin. */
    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    /** Boleta electrónica (SII) informada por notifications a través de Kafka. */
    @Column(name = "receipt_folio")
    private Long receiptFolio;

    @Column(name = "receipt_track_id", length = 64)
    private String receiptTrackId;

    @Column(name = "receipt_sii_status", length = 32)
    private String receiptSiiStatus;

    @Column(name = "receipt_issued_at")
    private Instant receiptIssuedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<OrderItem> items = new ArrayList<>();

    public Order() {
    }

    public Order(Long customerId, OrderStatus status, LocalDateTime createdAt, BigDecimal total) {
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

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(Instant paidAt) {
        this.paidAt = paidAt;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public void setPaymentReference(String paymentReference) {
        this.paymentReference = paymentReference;
    }

    public Long getReceiptFolio() {
        return receiptFolio;
    }

    public void setReceiptFolio(Long receiptFolio) {
        this.receiptFolio = receiptFolio;
    }

    public String getReceiptTrackId() {
        return receiptTrackId;
    }

    public void setReceiptTrackId(String receiptTrackId) {
        this.receiptTrackId = receiptTrackId;
    }

    public String getReceiptSiiStatus() {
        return receiptSiiStatus;
    }

    public void setReceiptSiiStatus(String receiptSiiStatus) {
        this.receiptSiiStatus = receiptSiiStatus;
    }

    public Instant getReceiptIssuedAt() {
        return receiptIssuedAt;
    }

    public void setReceiptIssuedAt(Instant receiptIssuedAt) {
        this.receiptIssuedAt = receiptIssuedAt;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }

    public void addItem(OrderItem item) {
        this.items.add(item);
        item.setOrder(this);
    }

    public void removeItem(OrderItem item) {
        this.items.remove(item);
        item.setOrder(null);
    }
}