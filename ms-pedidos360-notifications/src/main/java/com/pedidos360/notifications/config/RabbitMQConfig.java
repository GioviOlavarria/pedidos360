package com.pedidos360.notifications.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topología RabbitMQ del flujo "pedido pagado -> boleta por correo".
 * Debe ser idéntica a la declarada en ms-pedidos360-orders (si difiere, RabbitMQ rechaza la declaración).
 */
@Slf4j
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "pedidos360.orders.exchange";
    /** El pedido se pagó: dispara la emisión de la boleta. */
    public static final String ROUTING_KEY_ORDER_PAID = "order.paid";
    public static final String QUEUE_BOLETA = "pedidos360.notifications.boleta";
    public static final String DLX = "pedidos360.orders.dlx";
    public static final String QUEUE_BOLETA_DLQ = "pedidos360.notifications.boleta.dlq";

    @Bean
    public TopicExchange ordersExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    public Queue boletaQueue() {
        return QueueBuilder.durable(QUEUE_BOLETA)
                .quorum()
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(QUEUE_BOLETA_DLQ)
                .build();
    }

    @Bean
    public Queue boletaDeadLetterQueue() {
        return QueueBuilder.durable(QUEUE_BOLETA_DLQ).quorum().build();
    }

    @Bean
    public Binding boletaBinding() {
        return BindingBuilder.bind(boletaQueue()).to(ordersExchange()).with(ROUTING_KEY_ORDER_PAID);
    }

    @Bean
    public Binding boletaDeadLetterBinding() {
        return BindingBuilder.bind(boletaDeadLetterQueue()).to(deadLetterExchange()).with(QUEUE_BOLETA_DLQ);
    }

    /**
     * Agotados los reintentos del listener, rechaza el mensaje sin reencolar (RabbitMQ lo enruta a la DLQ por el DLX)
     * y deja en el log qué mensaje se fue a la DLQ y por qué.
     */
    @Bean
    public MessageRecoverer deadLetterRecoverer() {
        return new RejectAndDontRequeueRecoverer() {
            @Override
            public void recover(Message message, Throwable cause) {
                MessageProperties props = message.getMessageProperties();
                log.error("Mensaje enviado a la DLQ {}: cola={} routingKey={} messageId={} causa={} payload={}",
                        QUEUE_BOLETA_DLQ, props.getConsumerQueue(), props.getReceivedRoutingKey(), props.getMessageId(),
                        rootCause(cause), new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8));
                super.recover(message, cause);
            }
        };
    }

    private static String rootCause(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        // El header __TypeId__ trae la clase de orders; se usa el tipo del parámetro del listener.
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }
}
