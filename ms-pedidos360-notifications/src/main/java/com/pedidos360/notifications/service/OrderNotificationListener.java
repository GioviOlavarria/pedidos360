package com.pedidos360.notifications.service;

import com.pedidos360.notifications.config.RabbitMQConfig;
import com.pedidos360.notifications.model.OrderCreatedEvent;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;

/**
 * Consume los eventos "pedido creado" y envía la boleta por correo.
 * Si el envío falla, la excepción se propaga: Spring reintenta (ver spring.rabbitmq.listener.simple.retry)
 * y, agotados los reintentos, el mensaje pasa a la cola DLQ en lugar de perderse.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderNotificationListener {

    private final BoletaMailService boletaMailService;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_BOLETA)
    public void handleOrderCreated(OrderCreatedEvent event) throws MessagingException {
        log.info("Recibido evento de pedido #{} para {}", event.orderId(), event.customerEmail());

        if (event.customerEmail() == null || !event.customerEmail().contains("@")) {
            // Reintentar no lo arreglaría: va directo a la DLQ.
            throw new AmqpRejectAndDontRequeueException("Pedido #" + event.orderId() + " sin correo de destino");
        }

        try {
            boletaMailService.sendBoleta(event);
            log.info("Boleta del pedido #{} enviada a {}", event.orderId(), event.customerEmail());
        } catch (MailException | MessagingException e) {
            log.warn("Fallo al enviar la boleta del pedido #{} a {}: {}", event.orderId(), event.customerEmail(), e.getMessage());
            throw e;
        }
    }
}
