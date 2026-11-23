package com.pedidos360.notifications.service;

import com.pedidos360.notifications.config.RabbitMQConfig;
import com.pedidos360.notifications.kafka.ReceiptEventProducer;
import com.pedidos360.notifications.kafka.ReceiptIssuedMessage;
import com.pedidos360.notifications.model.OrderCreatedEvent;
import com.pedidos360.notifications.sii.BoletaElectronica;
import com.pedidos360.notifications.sii.SiiClient;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;

/**
 * Consume los eventos "pedido creado" de RabbitMQ: emite la boleta electrónica ante el SII, la envía por correo
 * y, si Kafka está activo, publica la boleta emitida en el tópico de boletas para que orders la registre.
 * Si el envío falla, la excepción se propaga: Spring reintenta (ver spring.rabbitmq.listener.simple.retry)
 * y, agotados los reintentos, el mensaje pasa a la cola DLQ en lugar de perderse.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderNotificationListener {

    private final SiiClient siiClient;
    private final BoletaMailService boletaMailService;
    private final ObjectProvider<ReceiptEventProducer> receiptEvents;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_BOLETA)
    public void handleOrderCreated(OrderCreatedEvent event) throws MessagingException {
        log.info("Recibido evento de pedido #{} para {}", event.orderId(), event.customerEmail());

        if (event.orderId() == null || event.customerEmail() == null || !event.customerEmail().contains("@")) {
            // Reintentar no lo arreglaría: va directo a la DLQ.
            throw new AmqpRejectAndDontRequeueException("Pedido #" + event.orderId() + " sin correo de destino");
        }

        // La emisión es idempotente por pedido: si el correo falla y se reintenta, se reenvía la misma boleta.
        BoletaElectronica boleta = siiClient.emitirBoleta(event);
        try {
            boletaMailService.sendBoleta(event, boleta);
            log.info("Boleta folio {} del pedido #{} enviada a {}", boleta.folio(), event.orderId(), event.customerEmail());
        } catch (MailException | MessagingException e) {
            log.warn("Fallo al enviar la boleta del pedido #{} a {}: {}", event.orderId(), event.customerEmail(), e.getMessage());
            throw e;
        }
        receiptEvents.ifAvailable(producer ->
                producer.publish(ReceiptIssuedMessage.of(event.orderId(), boleta, event.customerEmail())));
    }
}
