package com.pedidos360.orders.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topología RabbitMQ del flujo "pedido creado -> boleta por correo".
 *
 * Se declara de forma idéntica en orders y en notifications para que, aunque notifications
 * aún no haya arrancado, los mensajes publicados queden en la cola y no se pierdan.
 * Las colas son quorum (replicadas en el cluster de 3 nodos). Los mensajes que fallan tras
 * los reintentos de notifications van a la cola .dlq para revisión manual.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "pedidos360.orders.exchange";
    public static final String ROUTING_KEY_ORDER_CREATED = "order.created";
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
        return BindingBuilder.bind(boletaQueue()).to(ordersExchange()).with(ROUTING_KEY_ORDER_CREATED);
    }

    @Bean
    public Binding boletaDeadLetterBinding() {
        return BindingBuilder.bind(boletaDeadLetterQueue()).to(deadLetterExchange()).with(QUEUE_BOLETA_DLQ);
    }

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
