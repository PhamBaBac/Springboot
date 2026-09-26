package com.bacpham.kanban_service.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import com.bacpham.kanban_service.entity.Order;
import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.enums.OrderStatus;

@Repository
public interface OrderRepository extends JpaRepository<Order, String>, JpaSpecificationExecutor<Order> {

    /**
     * Fix A-5: Khôi phục trực tiếp trên database bằng 1 câu UPDATE duy nhất.
     * Tránh việc findAll() load toàn bộ Order vào bộ nhớ tại thời điểm khởi động
     * server (@PostConstruct).
     */
    @Modifying
    @Transactional
    @Query("UPDATE Order o SET o.deleted = false, o.customerHidden = true WHERE o.deleted = true")
    int recoverDeletedOrders();

    List<Order> findByUserAndDeletedFalse(User user);

    /**
     * Fix N+1: Eager load items + subProduct trong 1 query.
     * Không dùng @EntityGraph vì Spring Data không hỗ trợ tốt với collection fetch
     * + pagination.
     * Dùng JPQL JOIN FETCH thay thế.
     */
    @Query("""
                SELECT DISTINCT o FROM Order o
                LEFT JOIN FETCH o.items i
                LEFT JOIN FETCH i.subProduct sp
                LEFT JOIN FETCH sp.product
                WHERE o.user = :user
                  AND (o.customerHidden = false OR o.customerHidden IS NULL)
                  AND (o.deleted = false OR o.deleted IS NULL)
            """)
    List<Order> findByUserAndNotCustomerHidden(@Param("user") User user);

    /**
     * Fix N+1-2: Eager load user + address + items khi lấy paginated orders cho
     * admin.
     * Dùng countQuery riêng để pagination vẫn đúng khi có JOIN FETCH.
     */
    @Query(value = """
                SELECT DISTINCT o FROM Order o
                LEFT JOIN FETCH o.user u
                LEFT JOIN FETCH o.address a
                LEFT JOIN FETCH o.items i
                LEFT JOIN FETCH i.subProduct sp
                LEFT JOIN FETCH sp.product p
                WHERE o.deleted = false
            """, countQuery = "SELECT COUNT(o) FROM Order o WHERE o.deleted = false")
    Page<Order> findAllByDeletedFalse(Pageable pageable);

    @Query("""
                SELECT CASE WHEN COUNT(o) > 0 THEN true ELSE false END
                FROM Order o
                JOIN o.items i
                WHERE o.id = :orderId
                  AND o.user.id = :userId
                  AND o.orderStatus = :orderStatus
                  AND i.subProduct.id = :subProductId
            """)
    boolean existsByIdAndUserIdAndOrderStatusAndItemsSubProductId(
            @Param("orderId") String orderId,
            @Param("userId") String userId,
            @Param("orderStatus") OrderStatus orderStatus,
            @Param("subProductId") String subProductId);

    Optional<Order> findByTrackingCode(String trackingCode);

    /**
     * Fix RAM-4: Tính tổng doanh thu bằng DB aggregate — không cần load toàn bộ
     * Order vào memory
     */
    @Query("SELECT COALESCE(SUM(o.total), 0) FROM Order o WHERE o.orderStatus = :status AND (o.deleted = false OR o.deleted IS NULL)")
    double sumTotalByStatusAndDeletedFalse(@Param("status") com.bacpham.kanban_service.enums.OrderStatus status);

    /** Fix RAM-4: Đếm tổng số order không bị xóa bằng DB aggregate */
    @Query("SELECT COUNT(o) FROM Order o WHERE o.deleted = false OR o.deleted IS NULL")
    long countByDeletedFalse();

    @Query("SELECT o.orderStatus, COUNT(o) FROM Order o WHERE o.deleted = false OR o.deleted IS NULL GROUP BY o.orderStatus")
    List<Object[]> countOrdersByStatus();

    /**
     * Fix RAM-4: Tìm order theo id với eager fetch để tránh lazy N+1 khi truy cập
     * items.
     */
    @Query("""
                SELECT o FROM Order o
                LEFT JOIN FETCH o.items i
                LEFT JOIN FETCH i.subProduct sp
                LEFT JOIN FETCH sp.product p
                LEFT JOIN FETCH o.address a
                WHERE o.id = :id
            """)
    Optional<Order> findByIdWithDetails(@Param("id") String id);

    /**
     * Fix lazy loading for paginated admin orders: batch-fetch a page of orders
     * by their IDs with all associations eagerly loaded in a single query.
     * DISTINCT prevents duplicate Order rows caused by the collection JOIN FETCHes.
     */
    @Query("""
                SELECT DISTINCT o FROM Order o
                LEFT JOIN FETCH o.items i
                LEFT JOIN FETCH i.subProduct sp
                LEFT JOIN FETCH sp.product p
                LEFT JOIN FETCH o.address a
                WHERE o.id IN :ids
            """)
    List<Order> findAllWithItemsByIds(@Param("ids") List<String> ids);
}
