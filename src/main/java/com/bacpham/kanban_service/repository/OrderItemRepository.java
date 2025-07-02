package com.bacpham.kanban_service.repository;

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
        WHERE o.paymentType = 'COMPLETED'
        GROUP BY sp.product.id
        ORDER BY totalSold DESC LIMIT 8
    """)
    List<Object[]> findBestSellerProductIds();
}
