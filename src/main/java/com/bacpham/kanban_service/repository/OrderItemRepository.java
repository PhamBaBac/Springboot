package com.bacpham.kanban_service.repository;

import com.bacpham.kanban_service.dto.response.SubProductSellingInfo;
import com.bacpham.kanban_service.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, String> {
    @Query("""
        SELECT sp.product.id, SUM(oi.quantity) as totalSold
        FROM OrderItem oi
        JOIN oi.order o
        JOIN oi.subProduct sp
        WHERE o.orderStatus = 'COMPLETED'
        GROUP BY sp.product.id
        ORDER BY totalSold DESC LIMIT 8
    """)
    List<Object[]> findBestSellerProductIds();
    @Query("""
    SELECT new com.bacpham.kanban_service.dto.response.SubProductSellingInfo(
        p.title,
        sp.color,
        sp.size,
        SUM(oi.quantity),
        sp.stock,
        MAX(oi.priceAtOrderTime),
        sp.images
    )
    FROM OrderItem oi
    JOIN oi.subProduct sp
    JOIN sp.product p
    GROUP BY sp.id, p.title, sp.color, sp.size, sp.stock
    ORDER BY SUM(oi.quantity) DESC
     LIMIT 5
""")
    List<SubProductSellingInfo> findTopSellingSubProducts();
}
