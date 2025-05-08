package com.bacpham.kanban_service.repository;

import com.bacpham.kanban_service.entity.Product;
import com.bacpham.kanban_service.entity.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, String> {
    Page<Product> findByTitleContainingIgnoreCase(String title, Pageable pageable);
    Page<Product> findAllByDeletedFalse(Pageable pageable);
}