package com.bacpham.kanban_service.repository;

import com.bacpham.kanban_service.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewProductRepository extends JpaRepository<Review, String>{

    List<Review> findBySubProductId(String id);

    boolean existsByCreatedByIdAndSubProductIdAndOrderId(String userId, String subProductId, String orderId);

    List<Review> findBySubProductIdIn(List<String> subProductIds);
}
