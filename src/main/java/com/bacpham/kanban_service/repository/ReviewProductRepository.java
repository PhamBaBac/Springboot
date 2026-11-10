package com.bacpham.kanban_service.repository;

import com.bacpham.kanban_service.entity.Review;
import com.bacpham.kanban_service.enums.ReviewStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewProductRepository extends JpaRepository<Review, String>, JpaSpecificationExecutor<Review> {

    @Query("""
        SELECT r FROM Review r
        LEFT JOIN FETCH r.createdBy
        LEFT JOIN FETCH r.subProduct sp
        WHERE (r.status = 'APPROVED' OR r.status IS NULL)
          AND (r.deleted = false OR r.deleted IS NULL)
          AND (r.isFeatured = true OR (r.star >= 4 AND r.comment IS NOT NULL AND TRIM(r.comment) != ''))
        ORDER BY r.isFeatured DESC, r.createdAt DESC
    """)
    List<Review> findFeaturedReviews(Pageable pageable);

    @Query("""
        SELECT r FROM Review r
        WHERE r.subProduct.id = :id
          AND (r.status = 'APPROVED' OR r.status IS NULL)
          AND (r.deleted = false OR r.deleted IS NULL)
        ORDER BY r.createdAt DESC
    """)
    List<Review> findBySubProductId(@Param("id") String id);

    boolean existsByCreatedByIdAndSubProductIdAndOrderId(String userId, String subProductId, String orderId);

    @Query("""
        SELECT r FROM Review r
        WHERE r.subProduct.id IN :subProductIds
          AND (r.status = 'APPROVED' OR r.status IS NULL)
          AND (r.deleted = false OR r.deleted IS NULL)
        ORDER BY r.createdAt DESC
    """)
    List<Review> findBySubProductIdIn(@Param("subProductIds") List<String> subProductIds);

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

    @Query("SELECT COUNT(r) FROM Review r WHERE r.deleted = false OR r.deleted IS NULL")
    long countAllReviews();

    @Query("SELECT COALESCE(AVG(r.star), 0.0) FROM Review r WHERE (r.deleted = false OR r.deleted IS NULL) AND (r.status = 'APPROVED' OR r.status IS NULL)")
    Double getAverageApprovedRating();

    @Query("SELECT COUNT(r) FROM Review r WHERE (r.deleted = false OR r.deleted IS NULL) AND r.star = :star")
    long countByStar(@Param("star") int star);

    @Query("SELECT COUNT(r) FROM Review r WHERE (r.deleted = false OR r.deleted IS NULL) AND (r.status = 'APPROVED' OR r.status IS NULL)")
    long countApprovedReviews();

    @Query("SELECT COUNT(r) FROM Review r WHERE (r.deleted = false OR r.deleted IS NULL) AND r.status = :status")
    long countByStatus(@Param("status") ReviewStatus status);

    @Query("SELECT COUNT(r) FROM Review r WHERE (r.deleted = false OR r.deleted IS NULL) AND (r.shopReply IS NULL OR TRIM(r.shopReply) = '')")
    long countUnreplied();
}
