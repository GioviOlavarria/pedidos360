package com.pedidos360.notifications.sii;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Boleta electrónica (DTE tipo 39) emitida ante el SII para un pedido.
 *
 * @param documentType tipo de DTE: 39 = boleta electrónica afecta
 * @param folio        folio asignado (en el SII viene de un CAF autorizado)
 * @param rutEmisor    RUT de la empresa
 * @param rutReceptor  RUT del comprador; 66666666-6 = consumidor final sin RUT
 * @param netAmount    monto neto
 * @param taxAmount    IVA (19%)
 * @param total        monto total (los precios de la tienda incluyen IVA)
 * @param trackId      identificador de envío que entrega el SII para consultar el estado
 * @param siiStatus    estado del envío (SIMULADO en modo simulado; EPR/RCT/etc. con el SII real)
 * @param timbre       resumen del timbre electrónico (TED) que se imprime en la boleta
 */
public record BoletaElectronica(
        int documentType,
        long folio,
        String rutEmisor,
        String rutReceptor,
        BigDecimal netAmount,
        BigDecimal taxAmount,
        BigDecimal total,
        String trackId,
        String siiStatus,
        String timbre,
        Instant issuedAt) {

    public static final int BOLETA_AFECTA = 39;
    public static final String CONSUMIDOR_FINAL = "66666666-6";
}
