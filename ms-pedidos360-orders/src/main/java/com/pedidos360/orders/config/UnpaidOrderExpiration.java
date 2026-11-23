package com.pedidos360.orders.config;

import com.pedidos360.orders.domain.model.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;

/**
 * Cancela cada minuto los pedidos PENDIENTE_PAGO más antiguos que app.payments.expiration (30 min por defecto)
 * para devolver el stock reservado. Flow expira la orden de pago antes (timeout), así que no llegan pagos tardíos.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.payments.expiration-enabled", havingValue = "true", matchIfMissing = true)
public class UnpaidOrderExpiration {

    private static final Logger log = LoggerFactory.getLogger(UnpaidOrderExpiration.class);

    private final OrderService orderService;
    private final Duration expiration;

    public UnpaidOrderExpiration(OrderService orderService,
                                 @Value("${app.payments.expiration:PT30M}") Duration expiration) {
        this.orderService = orderService;
        this.expiration = expiration;
    }

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    public void expireUnpaidOrders() {
        int expired = orderService.expireUnpaid(expiration);
        if (expired > 0) {
            log.info("{} pedidos sin pagar cancelados por vencimiento", expired);
        }
    }
}
