package com.pedidos360.payments.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByToken(String token);

    List<Payment> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    List<Payment> findByOrderIdAndStatus(Long orderId, PaymentStatus status);

    /** Resultados finales que aún no se publicaron en Kafka. */
    List<Payment> findByEventPublishedFalseAndStatusNotAndCompletedAtBefore(PaymentStatus status, Instant before);
}
