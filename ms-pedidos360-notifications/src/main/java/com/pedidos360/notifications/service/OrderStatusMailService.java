package com.pedidos360.notifications.service;

import com.pedidos360.notifications.kafka.OrderEventMessage;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.util.Map;

/** Avisa al cliente por correo cada vez que su pedido cambia de estado (evento Kafka STATUS_CHANGED). */
@Service
public class OrderStatusMailService {

    private static final Logger log = LoggerFactory.getLogger(OrderStatusMailService.class);

    private static final Map<String, String> LABELS = Map.of(
            "PENDIENTE_PAGO", "Pendiente de pago",
            "CREADO", "Creado",
            "ACEPTADO", "Aceptado",
            "EN_PREPARACION", "En preparación",
            "DESPACHADO", "Despachado",
            "ENTREGADO", "Entregado",
            "CANCELADO", "Cancelado");

    private final JavaMailSender mailSender;
    private final String from;

    public OrderStatusMailService(JavaMailSender mailSender,
                                  @Value("${app.mail.from:Pedidos360 <noreply@pedidos360.com>}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void sendStatusChanged(OrderEventMessage event) throws MessagingException {
        String estado = label(event.status());
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(from);
        helper.setTo(event.customerEmail());
        helper.setSubject("Pedido #" + event.orderId() + ": " + estado);
        helper.setText(buildText(event), buildHtml(event));
        mailSender.send(message);
        log.info("Aviso de estado {} del pedido #{} enviado a {}", event.status(), event.orderId(), event.customerEmail());
    }

    String buildText(OrderEventMessage event) {
        return "Estimado/a " + nameOf(event) + ",\n\n"
                + "Su pedido N° " + event.orderId() + " cambió de estado: "
                + label(event.previousStatus()) + " -> " + label(event.status()) + ".\n\n"
                + "Puede seguirlo en la sección \"Seguimiento de Envíos\" con el número " + event.orderId() + ".\n\n"
                + "Saludos,\nEl equipo de Pedidos360";
    }

    String buildHtml(OrderEventMessage event) {
        return """
                <div style="font-family:Segoe UI,Arial,sans-serif;max-width:600px;margin:auto;color:#1e293b">
                  <div style="background:#003087;color:#fff;padding:20px 24px;border-radius:10px 10px 0 0">
                    <h1 style="margin:0;font-size:22px">Pedidos<span style="color:#FF6B00">360</span></h1>
                    <p style="margin:4px 0 0;opacity:.8">Actualización de su pedido</p>
                  </div>
                  <div style="border:1px solid #e2e8f0;border-top:none;padding:24px;border-radius:0 0 10px 10px">
                    <p>Estimado/a <strong>%s</strong>,</p>
                    <p>Su pedido <strong>#%d</strong> cambió de estado:</p>
                    <p style="font-size:18px"><span style="color:#64748b">%s</span> &rarr;
                       <strong style="color:#FF6B00">%s</strong></p>
                    <p style="font-size:13px;color:#64748b">Puede seguirlo en "Seguimiento de Envíos" con el número <strong>%d</strong>.</p>
                  </div>
                </div>
                """.formatted(HtmlUtils.htmlEscape(nameOf(event)), event.orderId(),
                label(event.previousStatus()), label(event.status()), event.orderId());
    }

    private static String label(String status) {
        return status == null ? "-" : LABELS.getOrDefault(status, status);
    }

    private static String nameOf(OrderEventMessage event) {
        return event.customerName() != null && !event.customerName().isBlank() ? event.customerName() : event.customerEmail();
    }
}
