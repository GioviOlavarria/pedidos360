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
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody OrderRequest request,
            org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken jwtToken) {
        
        String email = "cliente@correo.com";
        String name = "Cliente " + request.getCustomerId();
        
        if (jwtToken != null && jwtToken.getToken() != null) {
            if (jwtToken.getToken().getClaimAsString("preferred_username") != null) {
                email = jwtToken.getToken().getClaimAsString("preferred_username");
            } else if (jwtToken.getToken().getClaimAsString("email") != null) {
                email = jwtToken.getToken().getClaimAsString("email");
            } else if (jwtToken.getToken().getClaimAsString("unique_name") != null) {
                email = jwtToken.getToken().getClaimAsString("unique_name");
            }
            
            if (jwtToken.getToken().getClaimAsString("name") != null) {
                name = jwtToken.getToken().getClaimAsString("name");
            }
        }

        Order order = orderService.createOrder(request, email, name);
        OrderResponse response = mapToResponse(order);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<OrderResponse> updateOrder(@PathVariable Long id, @Valid @RequestBody OrderRequest request) {
        Order order = orderService.updateOrder(id, request);
        OrderResponse response = mapToResponse(order);
        return ResponseEntity.ok(response);
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

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable Long id) {
        orderService.deleteOrder(id);
        return ResponseEntity.noContent().build();
    }

    private OrderResponse mapToResponse(Order order) {
        OrderResponse response = new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getTotal()
        );
        
        if (order.getItems() != null) {
            java.util.List<com.pedidos360.orders.api.dto.OrderItemResponse> itemResponses = new java.util.ArrayList<>();
            for (com.pedidos360.orders.domain.model.OrderItem item : order.getItems()) {
                com.pedidos360.orders.api.dto.OrderItemResponse itemResp = new com.pedidos360.orders.api.dto.OrderItemResponse();
                itemResp.setId(item.getId());
                itemResp.setProductId(item.getProductId());
                itemResp.setQuantity(item.getQuantity());
                itemResp.setUnitPrice(item.getUnitPrice());
                itemResponses.add(itemResp);
            }
            response.setItems(itemResponses);
        }
        
        return response;
    }
}