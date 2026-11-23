package com.pedidos360.catalog.repository;

import com.pedidos360.catalog.dto.ProductSalesResponse;
import com.pedidos360.catalog.entity.OrderSalesLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OrderSalesLineRepository extends JpaRepository<OrderSalesLine, Long> {

    @Modifying
    @Query("delete from OrderSalesLine l where l.orderId = :orderId")
    int deleteByOrderId(Long orderId);

    @Query("""
            select new com.pedidos360.catalog.dto.ProductSalesResponse(
                l.productId, max(l.productName), sum(l.quantity), sum(l.subtotal), count(distinct l.orderId))
            from OrderSalesLine l
            group by l.productId
            order by sum(l.quantity) desc
            """)
    List<ProductSalesResponse> salesByProduct();
}
