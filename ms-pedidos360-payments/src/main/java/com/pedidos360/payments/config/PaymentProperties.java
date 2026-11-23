package com.pedidos360.payments.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuración de la pasarela (prefijo app.payments).
 *
 * @param provider     "flow" (API de Flow) o "simulado" (pasarela de prueba local, sin cuenta en Flow)
 * @param publicUrl    URL pública por la que Flow llega a este servicio (a través del BFF). Para recibir la
 *                     confirmación servidor a servidor de Flow debe ser accesible desde internet (p. ej. ngrok).
 * @param frontendUrl  URL del frontend al que vuelve el comprador después de pagar
 * @param flow         credenciales y URL de la API de Flow
 */
@ConfigurationProperties(prefix = "app.payments")
public record PaymentProperties(String provider, String publicUrl, String frontendUrl, Flow flow) {

    /**
     * @param apiUrl    https://sandbox.flow.cl/api (pruebas) o https://www.flow.cl/api (producción)
     * @param apiKey    apiKey del comercio (Flow → Mis datos → Seguridad)
     * @param secretKey secretKey con que se firman las peticiones (HMAC-SHA256)
     * @param timeout   tiempo que Flow mantiene abierta la orden de pago (menor al vencimiento del pedido)
     */
    public record Flow(String apiUrl, String apiKey, String secretKey, Duration timeout) {
    }

    public boolean isFlow() {
        return "flow".equalsIgnoreCase(provider);
    }
}
