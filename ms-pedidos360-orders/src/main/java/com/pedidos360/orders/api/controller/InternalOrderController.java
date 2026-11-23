package com.pedidos360.orders.api.controller;

import com.pedidos360.orders.domain.model.Order;
import com.pedidos360.orders.domain.model.service.OrderService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Consulta interna usada por ms-pedidos360-payments antes de cobrar: monto, dueño y estado del pedido.
 * No se expone a través del BFF y exige la cabecera X-Internal-Api-Key compartida entre microservicios.
 */
@RestController
@RequestMapping("/internal/orders")
public class InternalOrderController {

    public record PaymentInfo(Long id, String status, BigDecimal total, String customerEmail, String customerName,
                              int itemCount) {
    }

    private final OrderService orderService;
    private final byte[] internalApiKey;

    public InternalOrderController(OrderService orderService, @Value("${app.internal.api-key:}") String internalApiKey) {
        this.orderService = orderService;
        this.internalApiKey = internalApiKey.getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping("/{id}")
    public PaymentInfo paymentInfo(@RequestHeader(value = "X-Internal-Api-Key", required = false) String key,
                                   @PathVariable Long id) {
        if (internalApiKey.length == 0 || key == null
                || !MessageDigest.isEqual(internalApiKey, key.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso interno no autorizado");
        }
        Order order = orderService.findById(id);
        return new PaymentInfo(order.getId(), order.getStatus().name(), order.getTotal(), order.getCustomerEmail(),
                order.getCustomerName(), order.getItems().size());
    }
}
