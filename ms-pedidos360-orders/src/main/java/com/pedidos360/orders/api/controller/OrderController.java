package com.pedidos360.orders.api.controller;

import com.pedidos360.orders.api.dto.OrderRequest;
import com.pedidos360.orders.api.dto.OrderResponse;
import com.pedidos360.orders.domain.model.Order;
import com.pedidos360.orders.domain.model.OrderStatus;
import com.pedidos360.orders.domain.model.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody OrderRequest request) {
        Order order = orderService.createOrder(request.getCustomerId(), request.getTotal());
        OrderResponse response = mapToResponse(order);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> listAllOrders() {
        List<Order> orders = orderService.findAll();
        List<OrderResponse> responses = orders.stream()
                .map(this::mapToResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long id) {
        Order order = orderService.findById(id);
        OrderResponse response = mapToResponse(order);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderResponse> changeOrderStatus(
            @PathVariable Long id,
            @RequestParam OrderStatus status) {
        Order order = orderService.changeStatus(id, status);
        OrderResponse response = mapToResponse(order);
        return ResponseEntity.ok(response);
    }

    private OrderResponse mapToResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getTotal()
        );
    }
}