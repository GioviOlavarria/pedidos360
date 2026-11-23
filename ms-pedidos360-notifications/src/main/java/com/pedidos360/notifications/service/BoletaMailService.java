package com.pedidos360.notifications.service;

import com.pedidos360.notifications.model.OrderCreatedEvent;
import com.pedidos360.notifications.sii.BoletaElectronica;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Construye y envía por correo la boleta electrónica (emitida por {@link com.pedidos360.notifications.sii.SiiClient}). */
@Service
public class BoletaMailService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private final JavaMailSender mailSender;
    private final String from;

    public BoletaMailService(JavaMailSender mailSender,
                             @Value("${app.mail.from:Pedidos360 <noreply@pedidos360.com>}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void sendBoleta(OrderCreatedEvent event, BoletaElectronica boleta) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(from);
        helper.setTo(event.customerEmail());
        helper.setSubject("Boleta electrónica N° " + boleta.folio() + " - Pedido #" + event.orderId());
        helper.setText(buildText(event, boleta), buildHtml(event, boleta));
        mailSender.send(message);
    }

    String buildText(OrderCreatedEvent event, BoletaElectronica boleta) {
        StringBuilder sb = new StringBuilder();
        sb.append("Estimado/a ").append(nameOf(event)).append(",\n\n")
                .append("Gracias por su compra en Pedidos360. Este es el detalle de su boleta:\n\n")
                .append("Boleta electrónica N°: ").append(boleta.folio())
                .append(" (RUT emisor ").append(boleta.rutEmisor()).append(")\n")
                .append("Pedido N°: ").append(event.orderId()).append('\n')
                .append("Fecha: ").append(fecha(event.createdAt())).append("\n\n");
        for (OrderCreatedEvent.Item item : items(event)) {
            sb.append("- ").append(item.productName()).append(" x").append(item.quantity())
                    .append("  ").append(money(item.unitPrice())).append(" c/u = ").append(money(item.subtotal())).append('\n');
        }
        sb.append("\nNeto: ").append(money(boleta.netAmount())).append('\n')
                .append("IVA (19%): ").append(money(boleta.taxAmount())).append('\n')
                .append("TOTAL: ").append(money(boleta.total())).append("\n\n")
                .append("Timbre SII: ").append(boleta.timbre()).append(" | Track ID: ").append(boleta.trackId()).append("\n\n")
                .append("Puede seguir su pedido en la sección \"Seguimiento de Envíos\" con el número ")
                .append(event.orderId()).append(".\n\nSaludos,\nEl equipo de Pedidos360\n")
                .append(validez(boleta));
        return sb.toString();
    }

    String buildHtml(OrderCreatedEvent event, BoletaElectronica boleta) {
        StringBuilder rows = new StringBuilder();
        for (OrderCreatedEvent.Item item : items(event)) {
            rows.append("<tr>")
                    .append(td(esc(item.productName()), "left"))
                    .append(td(String.valueOf(item.quantity()), "center"))
                    .append(td(money(item.unitPrice()), "right"))
                    .append(td(money(item.subtotal()), "right"))
                    .append("</tr>");
        }

        return """
                <div style="font-family:Segoe UI,Arial,sans-serif;max-width:600px;margin:auto;color:#1e293b">
                  <div style="background:#003087;color:#fff;padding:20px 24px;border-radius:10px 10px 0 0">
                    <h1 style="margin:0;font-size:22px">Pedidos<span style="color:#FF6B00">360</span></h1>
                    <p style="margin:4px 0 0;opacity:.8">Boleta electrónica N° %d &middot; RUT %s</p>
                  </div>
                  <div style="border:1px solid #e2e8f0;border-top:none;padding:24px;border-radius:0 0 10px 10px">
                    <p>Estimado/a <strong>%s</strong>, gracias por su compra.</p>
                    <p style="margin:0">Pedido N° <strong>#%d</strong><br>Fecha: %s</p>
                    <table style="width:100%%;border-collapse:collapse;margin:20px 0;font-size:14px">
                      <thead><tr style="background:#f1f5f9">
                        <th style="padding:8px;text-align:left">Producto</th>
                        <th style="padding:8px">Cant.</th>
                        <th style="padding:8px;text-align:right">Precio unit.</th>
                        <th style="padding:8px;text-align:right">Subtotal</th>
                      </tr></thead>
                      <tbody>%s</tbody>
                      <tfoot><tr>
                        <td colspan="3" style="padding:6px 8px;text-align:right">Neto</td>
                        <td style="padding:6px 8px;text-align:right">%s</td>
                      </tr><tr>
                        <td colspan="3" style="padding:6px 8px;text-align:right">IVA (19%%)</td>
                        <td style="padding:6px 8px;text-align:right">%s</td>
                      </tr><tr>
                        <td colspan="3" style="padding:10px 8px;text-align:right;font-weight:700">TOTAL</td>
                        <td style="padding:10px 8px;text-align:right;font-weight:800;color:#FF6B00;font-size:16px">%s</td>
                      </tr></tfoot>
                    </table>
                    <p style="font-size:13px;color:#64748b">Puede seguir su pedido en "Seguimiento de Envíos" con el número <strong>%d</strong>.</p>
                    <p style="font-size:11px;color:#94a3b8">Timbre SII: %s &middot; Track ID: %s<br>%s</p>
                  </div>
                </div>
                """.formatted(boleta.folio(), esc(boleta.rutEmisor()), esc(nameOf(event)), event.orderId(),
                fecha(event.createdAt()), rows, money(boleta.netAmount()), money(boleta.taxAmount()),
                money(boleta.total()), event.orderId(), esc(boleta.timbre()), esc(boleta.trackId()), validez(boleta));
    }

    private static String validez(BoletaElectronica boleta) {
        return "SIMULADO".equals(boleta.siiStatus())
                ? "(Emisión SII simulada: documento sin validez tributaria)"
                : "Verifique este documento en www.sii.cl";
    }

    private static String td(String content, String align) {
        return "<td style=\"padding:8px;border-bottom:1px solid #f1f5f9;text-align:" + align + "\">" + content + "</td>";
    }

    private static List<OrderCreatedEvent.Item> items(OrderCreatedEvent event) {
        return event.items() != null ? event.items() : List.of();
    }

    private static String nameOf(OrderCreatedEvent event) {
        return event.customerName() != null && !event.customerName().isBlank() ? event.customerName() : event.customerEmail();
    }

    private static String esc(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value);
    }

    private static String fecha(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(FECHA) : "";
    }

    static String money(BigDecimal value) {
        DecimalFormat format = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-CL")));
        return "$" + format.format(value != null ? value : BigDecimal.ZERO);
    }
}
