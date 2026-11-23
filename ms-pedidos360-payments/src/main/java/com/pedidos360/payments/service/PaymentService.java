package com.pedidos360.payments.service;

import com.pedidos360.payments.client.OrdersClient;
import com.pedidos360.payments.client.OrdersClient.OrderInfo;
import com.pedidos360.payments.config.PaymentProperties;
import com.pedidos360.payments.domain.Payment;
import com.pedidos360.payments.domain.PaymentRepository;
import com.pedidos360.payments.domain.PaymentStatus;
import com.pedidos360.payments.gateway.PaymentGateway;
import com.pedidos360.payments.messaging.PaymentEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.RoundingMode;
import java.util.List;

/**
 * Flujo de pago:
 *  1. {@link #startCheckout}: valida el pedido con orders (dueño, estado PENDIENTE_PAGO y monto) y crea la orden
 *     de pago en la pasarela; el navegador se redirige a la URL que esta entrega.
 *  2. {@link #confirm}: al volver el comprador (urlReturn) o al avisar Flow (urlConfirmation) se consulta el
 *     estado real en la pasarela y, si es final, se publica el resultado en Kafka para orders.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final OrdersClient orders;
    private final PaymentEventPublisher events;
    private final PaymentProperties properties;

    public PaymentService(PaymentRepository payments, PaymentGateway gateway, OrdersClient orders,
                          PaymentEventPublisher events, PaymentProperties properties) {
        this.payments = payments;
        this.gateway = gateway;
        this.orders = orders;
        this.events = events;
        this.properties = properties;
    }

    public Payment startCheckout(Long orderId, Buyer buyer) {
        OrderInfo order = orders.getOrder(orderId);
        checkOwner(order, buyer);
        if (!"PENDIENTE_PAGO".equals(order.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El pedido #" + orderId + " no está pendiente de pago (estado " + order.status() + ")");
        }
        Payment payment = payments.save(new Payment(orderId, order.total().setScale(0, RoundingMode.HALF_UP),
                gateway.name(), order.customerEmail()));

        String base = properties.publicUrl() + "/api/payments/" + gateway.name().toLowerCase();
        PaymentGateway.Checkout checkout = gateway.create(payment,
                "Pedido #" + orderId + " Pedidos360 (" + order.itemCount() + " productos)",
                base + "/return", base + "/confirmation");
        payment.startCheckout(checkout.token(), checkout.providerReference(), checkout.redirectUrl());
        log.info("Pago {} del pedido #{} por ${} iniciado en {}", payment.getId(), orderId, payment.getAmount(),
                gateway.name());
        return payments.save(payment);
    }

    /**
     * Consulta el estado del pago en la pasarela y, si cambió a un estado final, lo guarda y lo publica.
     * Es idempotente: Flow puede avisar varias veces y el comprador puede recargar la página de retorno.
     */
    public Payment confirm(String token) {
        Payment payment = payments.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pago no encontrado"));
        if (payment.getStatus().isFinal()) {
            return payment;
        }
        PaymentGateway.Result result = gateway.status(token);
        PaymentStatus status = result.status();
        if (status == PaymentStatus.PAID && result.amount() != null
                && result.amount().compareTo(payment.getAmount()) != 0) {
            log.error("Pago {}: la pasarela informa ${} pero se esperaban ${}; se rechaza", payment.getId(),
                    result.amount(), payment.getAmount());
            status = PaymentStatus.REJECTED;
        }
        if (payment.complete(status, result.providerReference(), result.paymentMethod())) {
            payments.save(payment);
            log.info("Pago {} del pedido #{}: {}", payment.getId(), payment.getOrderId(), status);
            events.publish(payment);
        }
        return payment;
    }

    /** Vuelve a consultar en la pasarela los pagos pendientes de un pedido (si no llegó la confirmación). */
    public List<Payment> refresh(Long orderId, Buyer buyer) {
        checkOwner(orders.getOrder(orderId), buyer);
        payments.findByOrderIdAndStatus(orderId, PaymentStatus.PENDING).stream()
                .filter(p -> p.getToken() != null)
                .forEach(p -> confirm(p.getToken()));
        return payments.findByOrderIdOrderByCreatedAtDesc(orderId);
    }

    public List<Payment> forOrder(Long orderId, Buyer buyer) {
        checkOwner(orders.getOrder(orderId), buyer);
        return payments.findByOrderIdOrderByCreatedAtDesc(orderId);
    }

    public Payment byToken(String token, Buyer buyer) {
        Payment payment = payments.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pago no encontrado"));
        checkOwner(orders.getOrder(payment.getOrderId()), buyer);
        return payment;
    }

    private static void checkOwner(OrderInfo order, Buyer buyer) {
        boolean owner = buyer.email() != null && buyer.email().equalsIgnoreCase(order.customerEmail());
        if (!owner && !buyer.admin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El pedido #" + order.id() + " no es tuyo");
        }
    }

    /** Usuario autenticado (del JWT de Entra ID). */
    public record Buyer(String email, boolean admin) {
    }
}
