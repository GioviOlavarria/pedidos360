package com.pedidos360.notifications.service;

import com.pedidos360.notifications.kafka.ReceiptEventProducer;
import com.pedidos360.notifications.kafka.ReceiptIssuedMessage;
import com.pedidos360.notifications.model.OrderCreatedEvent;
import com.pedidos360.notifications.sii.BoletaElectronica;
import com.pedidos360.notifications.sii.SimulatedSiiClient;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mail.MailSendException;
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

    private static final SimulatedSiiClient SII = new SimulatedSiiClient("76123456-7");

    @Test
    void siiSplitsNetAndVatAndIsIdempotent() {
        BoletaElectronica boleta = SII.emitirBoleta(EVENT);

        assertEquals(39, boleta.documentType());
        assertEquals(42L, boleta.folio());
        assertEquals(new BigDecimal("7538"), boleta.netAmount());
        assertEquals(new BigDecimal("1432"), boleta.taxAmount());
        assertEquals(0, boleta.total().compareTo(boleta.netAmount().add(boleta.taxAmount())));
        assertEquals(BoletaElectronica.CONSUMIDOR_FINAL, boleta.rutReceptor());
        BoletaElectronica again = SII.emitirBoleta(EVENT);
        assertEquals(boleta.timbre(), again.timbre());
        assertEquals(boleta.trackId(), again.trackId());
    }

    @Test
    void boletaContainsDetailTaxesAndEscapesHtml() {
        BoletaMailService service = new BoletaMailService(new JavaMailSenderImpl(), "noreply@pedidos360.com");
        BoletaElectronica boleta = SII.emitirBoleta(EVENT);

        String html = service.buildHtml(EVENT, boleta);
        assertTrue(html.contains("#42"));
        assertTrue(html.contains("Caja Embalaje"));
        assertTrue(html.contains("$8.970"));
        assertTrue(html.contains("$1.432"));
        assertTrue(html.contains(boleta.timbre()));
        assertTrue(html.contains("Ana &lt;script&gt;"));
        assertFalse(html.contains("<script>"));

        String text = service.buildText(EVENT, boleta);
        assertTrue(text.contains("TOTAL: $8.970"));
        assertTrue(text.contains("IVA (19%): $1.432"));
        assertTrue(text.contains("20-10-2026 15:30"));
    }

    @Test
    void sendsMailToCustomer() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage mime = new JavaMailSenderImpl().createMimeMessage();
        when(sender.createMimeMessage()).thenReturn(mime);

        new BoletaMailService(sender, "noreply@pedidos360.com").sendBoleta(EVENT, SII.emitirBoleta(EVENT));

        verify(sender).send(mime);
        assertEquals("cliente@outlook.com", mime.getAllRecipients()[0].toString());
        assertEquals("Boleta electrónica N° 42 - Pedido #42", mime.getSubject());
    }

    @Test
    void eventWithoutEmailGoesToDeadLetterQueue() throws Exception {
        BoletaMailService mailService = mock(BoletaMailService.class);
        ReceiptEventProducer kafka = mock(ReceiptEventProducer.class);
        OrderNotificationListener listener = listener(mailService, kafka);
        OrderCreatedEvent noEmail = new OrderCreatedEvent(1L, null, "x", null, BigDecimal.ONE, List.of());

        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.handleOrderCreated(noEmail));
        verify(mailService, never()).sendBoleta(any(), any());
        verifyNoInteractions(kafka);
    }

    @Test
    void publishesIssuedReceiptToKafkaAfterSendingMail() throws Exception {
        BoletaMailService mailService = mock(BoletaMailService.class);
        ReceiptEventProducer kafka = mock(ReceiptEventProducer.class);

        listener(mailService, kafka).handleOrderCreated(EVENT);

        InOrder order = inOrder(mailService, kafka);
        order.verify(mailService).sendBoleta(eq(EVENT), any(BoletaElectronica.class));
        ArgumentCaptor<ReceiptIssuedMessage> message = ArgumentCaptor.forClass(ReceiptIssuedMessage.class);
        order.verify(kafka).publish(message.capture());
        assertEquals(42L, message.getValue().orderId());
        assertEquals(42L, message.getValue().folio());
        assertEquals("cliente@outlook.com", message.getValue().emailedTo());
    }

    @Test
    void mailFailureIsRethrownForRetryAndNothingIsPublished() throws Exception {
        BoletaMailService mailService = mock(BoletaMailService.class);
        ReceiptEventProducer kafka = mock(ReceiptEventProducer.class);
        doThrow(new MailSendException("SMTP caído")).when(mailService).sendBoleta(any(), any());

        assertThrows(MailSendException.class, () -> listener(mailService, kafka).handleOrderCreated(EVENT));
        verifyNoInteractions(kafka);
    }

    private static OrderNotificationListener listener(BoletaMailService mailService, ReceiptEventProducer kafka) {
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton("receiptEventProducer", kafka);
        return new OrderNotificationListener(SII, mailService, beans.getBeanProvider(ReceiptEventProducer.class));
    }
}
