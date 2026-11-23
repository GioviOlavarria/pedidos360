package com.pedidos360.rabbitadmin;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@SpringBootApplication
public class RabbitAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(RabbitAdminApplication.class, args);
    }

    @Bean
    AmqpAdmin amqpAdmin(ConnectionFactory connectionFactory) {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        // Este servicio declara recursos a pedido, no al arrancar.
        admin.setAutoStartup(false);
        return admin;
    }

    /** Cliente de la API HTTP de administración de RabbitMQ (plugin management), usado para listar recursos. */
    @Bean
    RestClient managementClient(@Value("${app.rabbitmq.management-url}") String url, RabbitProperties rabbit) {
        return RestClient.builder()
                .baseUrl(url)
                .defaultHeaders(headers -> headers.setBasicAuth(rabbit.getUsername(), rabbit.getPassword()))
                .build();
    }
}
