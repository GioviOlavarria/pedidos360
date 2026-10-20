package com.pedidos360.orders.messaging;

import com.pedidos360.orders.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publica el evento en RabbitMQ solo DESPUÉS de que el pedido quedó guardado (commit).
 * Así nunca se envía una boleta de un pedido que finalmente no se guardó, y una caída
 * de RabbitMQ no impide que el cliente compre (queda registrado en el log).
 */
@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public OrderEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderCreatedEvent event) {
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY_ORDER_CREATED, event);
            log.info("Evento order.created publicado para pedido #{} ({})", event.orderId(), event.customerEmail());
        } catch (AmqpException e) {
            log.error("No se pudo publicar el evento del pedido #{} en RabbitMQ: {}", event.orderId(), e.getMessage());
        }
    }
}
