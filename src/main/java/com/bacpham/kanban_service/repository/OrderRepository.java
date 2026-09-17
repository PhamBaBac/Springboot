package com.bacpham.kanban_service.repository;

import com.bacpham.kanban_service.entity.Order;
import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {
    List<Order> findByUserAndDeletedFalse(User user);

    @Query("""
        SELECT o FROM Order o
        WHERE o.user = :user
          AND (o.customerHidden = false OR o.customerHidden IS NULL)
          AND (o.deleted = false OR o.deleted IS NULL)
    """)
    List<Order> findByUserAndNotCustomerHidden(@Param("user") User user);

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
            @Param("subProductId") String subProductId
    );

    java.util.Optional<Order> findByTrackingCode(String trackingCode);
}
