package com.bacpham.kanban_service.repository;

import com.bacpham.kanban_service.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface CategoryRepository extends JpaRepository<Category, String> {
    @Modifying
    @Query(value = "DELETE FROM product_categories WHERE category_id = :categoryId", nativeQuery = true)
    void deleteCategoryFromProducts(@Param("categoryId") String categoryId);
}
