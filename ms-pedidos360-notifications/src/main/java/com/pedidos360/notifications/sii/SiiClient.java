package com.pedidos360.notifications.sii;

import com.pedidos360.notifications.model.OrderCreatedEvent;

/**
 * Puerto hacia el Servicio de Impuestos Internos (SII) para emitir la boleta electrónica de un pedido.
 *
 * La API REST de boleta electrónica del SII (apicert.sii.cl en certificación, api.sii.cl en producción) exige:
 * obtener una semilla (boleta.electronica.semilla), firmarla con el certificado digital del contribuyente para
 * pedir un token (boleta.electronica.token), firmar cada DTE con un folio de un CAF autorizado y enviarlo
 * (boleta.electronica.envio), y luego consultar el estado con el trackId. Como eso requiere certificado digital,
 * CAF y una empresa certificada, el proyecto incluye {@link SimulatedSiiClient}; un adaptador real solo debe
 * implementar esta interfaz y activarse con app.sii.mode.
 */
public interface SiiClient {

    /** Emite (o recupera, si ya se emitió) la boleta del pedido. Debe ser idempotente por orderId. */
    BoletaElectronica emitirBoleta(OrderCreatedEvent order);
}
