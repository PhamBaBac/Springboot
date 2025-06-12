package com.bacpham.kanban_service.repository;

import com.bacpham.kanban_service.entity.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, String> {

    Optional<Promotion> findByCode(String code);

    boolean existsByCode(String code);
}
