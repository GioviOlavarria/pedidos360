package com.pedidos360.notifications.service;

import com.pedidos360.notifications.config.RabbitMQConfig;
import com.pedidos360.notifications.model.OrderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderNotificationListener {

    private final JavaMailSender mailSender;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    public void handleOrderEvent(OrderEvent orderEvent) {
        log.info("Recibido evento de orden: {}", orderEvent);
        sendEmailReceipt(orderEvent);
    }

    private void sendEmailReceipt(OrderEvent orderEvent) {
        log.info("Enviando boleta al correo: {}", orderEvent.getCustomerEmail());
        
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("noreply@pedidos360.com");
            message.setTo(orderEvent.getCustomerEmail());
            message.setSubject("Boleta de su compra - Orden #" + orderEvent.getOrderId());
            message.setText("Estimado/a " + orderEvent.getCustomerName() + ",\n\n" +
                    "Gracias por su compra en Pedidos360.\n\n" +
                    "Detalles de la orden:\n" +
                    "ID de Orden: " + orderEvent.getOrderId() + "\n" +
                    "Total a pagar: $" + orderEvent.getTotalAmount() + "\n\n" +
                    "Si tiene alguna duda, puede contactarnos.\n" +
                    "Saludos,\nEl equipo de Pedidos360");
            
            mailSender.send(message);
            log.info("Correo enviado exitosamente a {}", orderEvent.getCustomerEmail());
        } catch (Exception e) {
            log.error("Error al enviar el correo a {}: {}", orderEvent.getCustomerEmail(), e.getMessage());
        }
    }
}
