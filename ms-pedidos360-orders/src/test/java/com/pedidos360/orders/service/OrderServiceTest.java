package com.pedidos360.orders.service;

import com.pedidos360.orders.domain.model.Order;
import com.pedidos360.orders.domain.model.OrderStatus;
import com.pedidos360.orders.domain.model.exception.InvalidOrderStateException;
import com.pedidos360.orders.domain.model.service.OrderService;
import com.pedidos360.orders.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService Tests")
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderService orderService;

    private Order order;

    @BeforeEach
    void setUp() {
        order = new Order();
        order.setId(1L);
        order.setCustomerId(100L);
        order.setStatus(OrderStatus.CREADO);
        order.setCreatedAt(LocalDateTime.now());
        order.setTotal(new BigDecimal("500.00"));
    }

    @Test
    @DisplayName("Transición válida CREADO -> ACEPTADO debe cambiar estado correctamente")
    void testValidTransitionFromCreadoToAceptado() {
        // Arrange
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        // Act
        Order result = orderService.changeStatus(1L, OrderStatus.ACEPTADO);

        // Assert
        assertNotNull(result);
        assertEquals(OrderStatus.ACEPTADO, order.getStatus());
        verify(orderRepository, times(1)).findById(1L);
        verify(orderRepository, times(1)).save(order);
    }

    @Test
    @DisplayName("Transición inválida CREADO -> DESPACHADO debe lanzar InvalidOrderStateException")
    void testInvalidTransitionFromCreadoToDespachadoThrowsException() {
        // Arrange
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        // Act & Assert
        InvalidOrderStateException exception = assertThrows(
                InvalidOrderStateException.class,
                () -> orderService.changeStatus(1L, OrderStatus.DESPACHADO),
                "Debería lanzar InvalidOrderStateException"
        );

        assertTrue(exception.getMessage().contains("CREADO") || exception.getMessage().contains("DESPACHADO"));
        verify(orderRepository, times(1)).findById(1L);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Crear pedido válido debe guardar correctamente vía repositorio")
    void testCreateOrderSavesCorrectlyViaRepository() {
        // Arrange
        Long customerId = 100L;
        BigDecimal total = new BigDecimal("750.00");

        Order createdOrder = new Order();
        createdOrder.setId(1L);
        createdOrder.setCustomerId(customerId);
        createdOrder.setStatus(OrderStatus.CREADO);
        createdOrder.setCreatedAt(LocalDateTime.now());
        createdOrder.setTotal(total);

        when(orderRepository.save(any(Order.class))).thenReturn(createdOrder);

        // Act
        com.pedidos360.orders.api.dto.OrderRequest request = new com.pedidos360.orders.api.dto.OrderRequest(customerId, total);
        request.setItems(java.util.Collections.emptyList());
        Order result = orderService.createOrder(request);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(customerId, result.getCustomerId());
        assertEquals(OrderStatus.CREADO, result.getStatus());
        assertEquals(total, result.getTotal());
        assertNotNull(result.getCreatedAt());

        verify(orderRepository, times(1)).save(any(Order.class));
    }
}