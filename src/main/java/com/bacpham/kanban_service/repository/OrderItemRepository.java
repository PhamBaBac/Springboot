package com.bacpham.kanban_service.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.bacpham.kanban_service.dto.response.SubProductSellingInfo;
import com.bacpham.kanban_service.entity.OrderItem;

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
    JOIN oi.order o
    JOIN oi.subProduct sp
    JOIN sp.product p
    WHERE o.orderStatus = 'COMPLETED' AND (o.deleted IS NULL OR o.deleted = false)
    GROUP BY sp.id, p.title, sp.color, sp.size, sp.stock
    ORDER BY SUM(oi.quantity) DESC
     LIMIT 5
""")
    List<SubProductSellingInfo> findTopSellingSubProducts();

    /**
     * Fix RAM-4: Thay findAll() bằng query giới hạn N items gần nhất.
     * Dùng cho "recent sales" trong Statistics dashboard.
     * @param limit số lượng items muốn lấy (ví dụ: 20)
     */
    @Query("""
        SELECT oi FROM OrderItem oi
        JOIN FETCH oi.order o
        JOIN FETCH oi.subProduct sp
        JOIN FETCH sp.product p
        WHERE (o.deleted IS NULL OR o.deleted = false)
        ORDER BY o.createdAt DESC
    """)
    List<OrderItem> findRecentOrderItems(Pageable pageable);

    default List<OrderItem> findRecentOrderItems(int limit) {
        return findRecentOrderItems(org.springframework.data.domain.PageRequest.of(0, limit));
    }
}
