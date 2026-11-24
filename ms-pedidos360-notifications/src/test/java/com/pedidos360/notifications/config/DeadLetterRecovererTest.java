package com.pedidos360.notifications.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.mail.MailSendException;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeadLetterRecovererTest {

    @Test
    void exhaustedMessageIsRejectedWithoutRequeueSoItGoesToTheDlq() {
        MessageRecoverer recoverer = new RabbitMQConfig().deadLetterRecoverer();
        MessageProperties props = new MessageProperties();
        props.setConsumerQueue(RabbitMQConfig.QUEUE_BOLETA);
        props.setReceivedRoutingKey(RabbitMQConfig.ROUTING_KEY_ORDER_PAID);
        Message message = new Message("{\"orderId\":1}".getBytes(StandardCharsets.UTF_8), props);

        // El contenedor ve el AmqpRejectAndDontRequeueException en la causa y rechaza sin reencolar: el DLX lo lleva a la DLQ.
        Exception ex = assertThrows(Exception.class,
                () -> recoverer.recover(message, new RuntimeException(new MailSendException("SMTP caído"))));
        assertInstanceOf(AmqpRejectAndDontRequeueException.class,
                ex instanceof AmqpRejectAndDontRequeueException ? ex : ex.getCause());
    }
}
