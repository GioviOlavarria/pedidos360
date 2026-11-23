package com.pedidos360.notifications.sii;

import com.pedidos360.notifications.model.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Emisión simulada de boletas: aplica las mismas reglas de cálculo que el SII (precio con IVA incluido,
 * neto = total / 1,19 redondeado, IVA = total - neto) y genera folio, trackId y timbre deterministas a partir
 * del pedido, de modo que reintentar la emisión entrega exactamente la misma boleta (idempotente).
 */
@Component
@ConditionalOnProperty(name = "app.sii.mode", havingValue = "simulado", matchIfMissing = true)
public class SimulatedSiiClient implements SiiClient {

    private static final Logger log = LoggerFactory.getLogger(SimulatedSiiClient.class);
    private static final BigDecimal IVA_FACTOR = new BigDecimal("1.19");

    private final String rutEmisor;

    public SimulatedSiiClient(@Value("${app.sii.rut-emisor:76123456-7}") String rutEmisor) {
        this.rutEmisor = rutEmisor;
    }

    @Override
    public BoletaElectronica emitirBoleta(OrderCreatedEvent order) {
        BigDecimal total = order.total() != null ? order.total().setScale(0, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal neto = total.divide(IVA_FACTOR, 0, RoundingMode.HALF_UP);
        BigDecimal iva = total.subtract(neto);
        long folio = order.orderId();
        String timbre = timbre(folio, total, order);
        String trackId = "SIM-" + timbre.substring(0, 10).toUpperCase();

        BoletaElectronica boleta = new BoletaElectronica(BoletaElectronica.BOLETA_AFECTA, folio, rutEmisor,
                BoletaElectronica.CONSUMIDOR_FINAL, neto, iva, total, trackId, "SIMULADO", timbre, Instant.now());
        log.info("SII (simulado): boleta {} folio {} emitida para pedido #{}: neto {} IVA {} total {} track {}",
                boleta.documentType(), folio, order.orderId(), neto, iva, total, trackId);
        return boleta;
    }

    private String timbre(long folio, BigDecimal total, OrderCreatedEvent order) {
        String ted = rutEmisor + "|" + BoletaElectronica.BOLETA_AFECTA + "|" + folio + "|" + total.toPlainString()
                + "|" + order.createdAt();
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(ted.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
