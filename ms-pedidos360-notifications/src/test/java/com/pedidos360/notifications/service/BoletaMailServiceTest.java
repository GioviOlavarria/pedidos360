package com.pedidos360.notifications.service;

import com.pedidos360.notifications.model.OrderCreatedEvent;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BoletaMailServiceTest {

    private static final OrderCreatedEvent EVENT = new OrderCreatedEvent(
            42L, "cliente@outlook.com", "Ana <script>", LocalDateTime.of(2026, 10, 20, 15, 30),
            new BigDecimal("8970.00"),
            List.of(new OrderCreatedEvent.Item(7L, "Caja Embalaje", 3, new BigDecimal("2990.00"), new BigDecimal("8970.00"))));

    @Test
    void boletaContainsDetailAndEscapesHtml() {
        BoletaMailService service = new BoletaMailService(new JavaMailSenderImpl(), "noreply@pedidos360.com");

        String html = service.buildHtml(EVENT);
        assertTrue(html.contains("#42"));
        assertTrue(html.contains("Caja Embalaje"));
        assertTrue(html.contains("$8.970"));
        assertTrue(html.contains("Ana &lt;script&gt;"));
        assertFalse(html.contains("<script>"));

        String text = service.buildText(EVENT);
        assertTrue(text.contains("TOTAL: $8.970"));
        assertTrue(text.contains("20-10-2026 15:30"));
    }

    @Test
    void sendsMailToCustomer() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage mime = new JavaMailSenderImpl().createMimeMessage();
        when(sender.createMimeMessage()).thenReturn(mime);

        new BoletaMailService(sender, "noreply@pedidos360.com").sendBoleta(EVENT);

        verify(sender).send(mime);
        assertEquals("cliente@outlook.com", mime.getAllRecipients()[0].toString());
        assertEquals("Boleta de su compra - Pedido #42", mime.getSubject());
    }

    @Test
    void eventWithoutEmailGoesToDeadLetterQueue() throws Exception {
        BoletaMailService mailService = mock(BoletaMailService.class);
        var beans = new DefaultListableBeanFactory();
        var kafka = mock(NotificationKafkaEvents.class);
        beans.registerSingleton("notificationKafkaEvents", kafka);
        var listener = new OrderNotificationListener(mailService, beans.getBeanProvider(NotificationKafkaEvents.class));
        OrderCreatedEvent noEmail = new OrderCreatedEvent(1L, null, "x", null, BigDecimal.ONE, List.of());

        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.handleOrderCreated(noEmail));
        verify(mailService, never()).sendBoleta(any());
        verifyNoInteractions(kafka);
    }

    @Test
    void publishesKafkaConfirmationAfterSendingMail() throws Exception {
        var mailService = mock(BoletaMailService.class);
        var kafka = mock(NotificationKafkaEvents.class);
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("notificationKafkaEvents", kafka);
        var listener = new OrderNotificationListener(mailService, beans.getBeanProvider(NotificationKafkaEvents.class));

        listener.handleOrderCreated(EVENT);

        InOrder order = inOrder(mailService, kafka);
        order.verify(mailService).sendBoleta(EVENT);
        order.verify(kafka).publishReceiptSent(EVENT.orderId());
    }
}
