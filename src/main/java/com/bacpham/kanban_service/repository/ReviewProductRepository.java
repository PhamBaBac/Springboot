package com.bacpham.kanban_service.repository;

import com.bacpham.kanban_service.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewProductRepository extends JpaRepository<Review, String> {

    List<Review> findBySubProductId(String id);

    boolean existsByCreatedByIdAndSubProductIdAndOrderId(String userId, String subProductId, String orderId);

    List<Review> findBySubProductIdIn(List<String> subProductIds);

    /**
     * Fix N+1: Thay N×M queries `existsBy` bằng 1 query batch duy nhất.
     * Dùng trong getOrdersByUserId để check reviewed status cho tất cả items cùng lúc.
     */
    @Query("""
        SELECT r FROM Review r
        WHERE r.createdBy.id = :userId
          AND r.subProduct.id IN :subProductIds
          AND r.order.id IN :orderIds
    """)
    List<Review> findByCreatedByIdAndSubProductIdInAndOrderIdIn(
            @Param("userId") String userId,
            @Param("subProductIds") List<String> subProductIds,
            @Param("orderIds") List<String> orderIds
    );
}
