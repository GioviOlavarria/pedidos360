package com.pedidos360.orders.service;

import com.pedidos360.orders.api.dto.OrderItemRequest;
import com.pedidos360.orders.api.dto.OrderRequest;
import com.pedidos360.orders.client.CatalogClient;
import com.pedidos360.orders.client.CatalogClient.ReservedItem;
import com.pedidos360.orders.client.CatalogClient.StockItem;
import com.pedidos360.orders.domain.model.Order;
import com.pedidos360.orders.domain.model.OrderItem;
import com.pedidos360.orders.domain.model.OrderStatus;
import com.pedidos360.orders.domain.model.exception.InvalidOrderStateException;
import com.pedidos360.orders.domain.model.exception.StockUnavailableException;
import com.pedidos360.orders.domain.model.service.CurrentUser;
import com.pedidos360.orders.domain.model.service.OrderService;
import com.pedidos360.orders.messaging.OrderCreatedEvent;
import com.pedidos360.orders.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService Tests")
class OrderServiceTest {

    private static final CurrentUser CUSTOMER = new CurrentUser("cliente@outlook.com", "Cliente Test", false, false);

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CatalogClient catalogClient;

    @Mock
    private ApplicationEventPublisher eventPublisher;

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
        order.addItem(new OrderItem(7L, "Caja", 2, new BigDecimal("250.00")));
    }

    @Test
    @DisplayName("Transición válida CREADO -> ACEPTADO debe cambiar estado correctamente")
    void testValidTransitionFromCreadoToAceptado() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        Order result = orderService.changeStatus(1L, OrderStatus.ACEPTADO);

        assertNotNull(result);
        assertEquals(OrderStatus.ACEPTADO, order.getStatus());
        verify(orderRepository).save(order);
        verifyNoInteractions(catalogClient);
    }

    @Test
    @DisplayName("Transición inválida CREADO -> DESPACHADO debe lanzar InvalidOrderStateException")
    void testInvalidTransitionFromCreadoToDespachadoThrowsException() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        InvalidOrderStateException exception = assertThrows(
                InvalidOrderStateException.class,
                () -> orderService.changeStatus(1L, OrderStatus.DESPACHADO));

        assertTrue(exception.getMessage().contains("CREADO"));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Cancelar un pedido devuelve el stock al catálogo")
    void testCancelReleasesStock() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        orderService.changeStatus(1L, OrderStatus.CANCELADO);

        verify(catalogClient).release(List.of(new StockItem(7L, 2)));
    }

    @Test
    @DisplayName("Crear pedido usa precios del catálogo, calcula el total y publica el evento de boleta")
    void testCreateOrderUsesCatalogPricesAndPublishesEvent() {
        when(catalogClient.reserve(List.of(new StockItem(7L, 3))))
                .thenReturn(List.of(new ReservedItem(7L, "Caja", new BigDecimal("2990.00"), 3)));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(10L);
            return o;
        });

        // El navegador intenta pagar $1 por unidad: debe ignorarse.
        OrderRequest request = request(7L, 3, "1");
        Order result = orderService.createOrder(request, CUSTOMER);

        assertEquals(new BigDecimal("8970.00"), result.getTotal());
        assertEquals("cliente@outlook.com", result.getCustomerEmail());
        assertEquals(OrderStatus.CREADO, result.getStatus());
        assertEquals("Caja", result.getItems().get(0).getProductName());

        ArgumentCaptor<OrderCreatedEvent> event = ArgumentCaptor.forClass(OrderCreatedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertEquals(10L, event.getValue().orderId());
        assertEquals("cliente@outlook.com", event.getValue().customerEmail());
        assertEquals(new BigDecimal("8970.00"), event.getValue().items().get(0).subtotal());
    }

    @Test
    @DisplayName("Sin stock no se guarda el pedido ni se envía boleta")
    void testCreateOrderWithoutStock() {
        when(catalogClient.reserve(any())).thenThrow(new StockUnavailableException("Stock insuficiente"));

        assertThrows(StockUnavailableException.class, () -> orderService.createOrder(request(7L, 99, null), CUSTOMER));

        verify(orderRepository, never()).saveAndFlush(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Si falla el guardado se devuelve el stock reservado")
    void testCreateOrderReleasesStockWhenSaveFails() {
        when(catalogClient.reserve(any()))
                .thenReturn(List.of(new ReservedItem(7L, "Caja", new BigDecimal("10"), 1)));
        when(orderRepository.saveAndFlush(any())).thenThrow(new RuntimeException("DB caída"));

        assertThrows(RuntimeException.class, () -> orderService.createOrder(request(7L, 1, null), CUSTOMER));

        verify(catalogClient).release(List.of(new StockItem(7L, 1)));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Un cliente solo ve sus propios pedidos")
    void testCustomerSeesOnlyOwnOrders() {
        orderService.findAllFor(CUSTOMER);
        verify(orderRepository).findByCustomerEmailIgnoreCaseOrderByCreatedAtDesc("cliente@outlook.com");
        verify(orderRepository, never()).findAllByOrderByCreatedAtDesc();
    }

    private static OrderRequest request(Long productId, int quantity, String unitPrice) {
        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(productId);
        item.setQuantity(quantity);
        if (unitPrice != null) item.setUnitPrice(new BigDecimal(unitPrice));
        OrderRequest request = new OrderRequest(100L, new BigDecimal("1"));
        request.setItems(List.of(item));
        return request;
    }
}
