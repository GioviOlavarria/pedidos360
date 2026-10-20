package com.pedidos360.orders.api.controller;

import com.pedidos360.orders.api.dto.OrderItemResponse;
import com.pedidos360.orders.api.dto.OrderRequest;
import com.pedidos360.orders.api.dto.OrderResponse;
import com.pedidos360.orders.domain.model.Order;
import com.pedidos360.orders.domain.model.OrderItem;
import com.pedidos360.orders.domain.model.OrderStatus;
import com.pedidos360.orders.domain.model.service.CurrentUser;
import com.pedidos360.orders.domain.model.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody OrderRequest request,
                                                     Authentication authentication) {
        Order order = orderService.createOrder(request, CurrentUser.from(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapToResponse(order));
    }

    @PutMapping("/{id}")
    public ResponseEntity<OrderResponse> updateOrder(@PathVariable Long id, @Valid @RequestBody OrderRequest request) {
        return ResponseEntity.ok(mapToResponse(orderService.updateOrder(id, request)));
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> listOrders(Authentication authentication) {
        List<OrderResponse> responses = orderService.findAllFor(CurrentUser.from(authentication)).stream()
                .map(this::mapToResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(mapToResponse(orderService.findById(id)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderResponse> changeOrderStatus(@PathVariable Long id, @RequestParam OrderStatus status) {
        return ResponseEntity.ok(mapToResponse(orderService.changeStatus(id, status)));
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
        response.setCustomerName(order.getCustomerName());

        List<OrderItemResponse> itemResponses = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            OrderItemResponse itemResp = new OrderItemResponse();
            itemResp.setId(item.getId());
            itemResp.setProductId(item.getProductId());
            itemResp.setProductName(item.getProductName());
            itemResp.setQuantity(item.getQuantity());
            itemResp.setUnitPrice(item.getUnitPrice());
            itemResponses.add(itemResp);
        }
        response.setItems(itemResponses);
        return response;
    }
}
