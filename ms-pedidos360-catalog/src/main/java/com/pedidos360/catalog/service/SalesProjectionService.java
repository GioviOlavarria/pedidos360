package com.pedidos360.catalog.service;

import com.pedidos360.catalog.dto.ProductSalesResponse;
import com.pedidos360.catalog.entity.OrderSalesLine;
import com.pedidos360.catalog.kafka.OrderEventMessage;
import com.pedidos360.catalog.kafka.OrderEventType;
import com.pedidos360.catalog.repository.OrderSalesLineRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Mantiene la estadística de ventas por producto a partir de los eventos de pedidos.
 * Cada evento trae el estado completo del pedido, así que basta con reemplazar sus líneas:
 * es idempotente (reprocesar no duplica) y el orden por pedido lo garantiza la clave del tópico.
 */
@Service
public class SalesProjectionService {

    private static final Logger log = LoggerFactory.getLogger(SalesProjectionService.class);

    private final OrderSalesLineRepository repository;

    public SalesProjectionService(OrderSalesLineRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void apply(OrderEventMessage event) {
        int removed = repository.deleteByOrderId(event.orderId());
        if (event.type() == OrderEventType.DELETED || "CANCELADO".equals(event.status())) {
            log.info("Ventas: pedido #{} {} -> se quitan {} líneas", event.orderId(), event.type(), removed);
            return;
        }
        List<OrderSalesLine> lines = (event.items() != null ? event.items() : List.<OrderEventMessage.Item>of()).stream()
                .map(i -> new OrderSalesLine(event.orderId(), i.productId(), i.productName(), i.quantity(),
                        i.subtotal() != null ? i.subtotal() : BigDecimal.ZERO))
                .toList();
        repository.saveAll(lines);
        log.info("Ventas: pedido #{} {} -> {} líneas", event.orderId(), event.type(), lines.size());
    }

    @Transactional(readOnly = true)
    public List<ProductSalesResponse> salesByProduct() {
        return repository.salesByProduct();
    }
}
