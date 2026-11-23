package com.pedidos360.payments.gateway;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * Firma de Flow: se ordenan los parámetros por nombre, se concatenan "nombrevalor" sin separadores y se
 * calcula HMAC-SHA256 con la secretKey del comercio. El resultado (hex) se envía en el parámetro "s".
 */
public final class FlowSigner {

    private FlowSigner() {
    }

    public static String sign(Map<String, String> params, String secretKey) {
        StringBuilder toSign = new StringBuilder();
        new TreeMap<>(params).forEach((name, value) -> {
            if (!"s".equals(name)) {
                toSign.append(name).append(value);
            }
        });
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(toSign.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo firmar la petición a Flow", e);
        }
    }
}
