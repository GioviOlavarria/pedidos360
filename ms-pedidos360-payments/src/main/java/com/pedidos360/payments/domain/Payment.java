package com.pedidos360.payments.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

/** Intento de pago de un pedido. Un pedido puede tener varios intentos (p. ej. si el primero fue rechazado). */
@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_payments_order", columnList = "order_id"),
        @Index(name = "idx_payments_token", columnList = "token", unique = true)
})
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    /** Monto en pesos (CLP no tiene decimales). */
    @Column(nullable = false, precision = 12, scale = 0)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "CLP";

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    /** FLOW o SIMULADO. */
    @Column(nullable = false, length = 20)
    private String provider;

    /** Token de la orden de pago en la pasarela. */
    @Column(length = 100)
    private String token;

    /** Número de orden en Flow (flowOrder). */
    @Column(name = "provider_reference", length = 50)
    private String providerReference;

    @Column(name = "payer_email", length = 320)
    private String payerEmail;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "redirect_url", length = 500)
    private String redirectUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    /** El resultado final ya se publicó en Kafka (si falló, un job lo reintenta). */
    @Column(name = "event_published", nullable = false)
    private boolean eventPublished;

    protected Payment() {
    }

    public Payment(Long orderId, BigDecimal amount, String provider, String payerEmail) {
        this.orderId = orderId;
        this.amount = amount;
        this.provider = provider;
        this.payerEmail = payerEmail;
    }

    /** Registra el resultado final. Devuelve false si el pago ya estaba cerrado (idempotencia). */
    public boolean complete(PaymentStatus result, String providerReference, String paymentMethod) {
        if (status.isFinal() || !result.isFinal()) {
            return false;
        }
        this.status = result;
        this.providerReference = providerReference != null ? providerReference : this.providerReference;
        this.paymentMethod = paymentMethod;
        this.completedAt = Instant.now();
        this.eventPublished = false;
        return true;
    }

    public void startCheckout(String token, String providerReference, String redirectUrl) {
        this.token = token;
        this.providerReference = providerReference;
        this.redirectUrl = redirectUrl;
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getProvider() {
        return provider;
    }

    public String getToken() {
        return token;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public String getPayerEmail() {
        return payerEmail;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public boolean isEventPublished() {
        return eventPublished;
    }

    public void markEventPublished() {
        this.eventPublished = true;
    }
}
