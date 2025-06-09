package com.bacpham.kanban_service.repository;

import com.bacpham.kanban_service.entity.Category;
import com.bacpham.kanban_service.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Repository
public interface ProductRepository extends JpaRepository<Product, String> {

    Page<Product> findByTitleContainingIgnoreCase(String title, Pageable pageable);

    Page<Product> findAllByDeletedFalse(Pageable pageable);
    @Query("""
   SELECT DISTINCT p FROM Product p
   JOIN p.categories c
   JOIN p.subProducts sp
   WHERE p.deleted = false
     AND (:categoryIds IS NULL OR c.id IN :categoryIds)
     AND (:size IS NULL OR sp.size = :size)
     AND (:colors IS NULL OR sp.color IN :colors)
     AND (
         (:minPrice IS NULL AND :maxPrice IS NULL)
         OR (:minPrice = :maxPrice AND sp.price = :minPrice)
         OR (:minPrice <> :maxPrice AND sp.price BETWEEN :minPrice AND :maxPrice)
     )
""")

    List<Product> findFilteredProducts(
            @Param("categoryIds") List<String> categoryIds,
            @Param("size") String size,
            @Param("colors") List<String> colors,
            @Param("minPrice") Double minPrice,
            @Param("maxPrice") Double maxPrice
    );

    @Query("""
        SELECT DISTINCT p FROM Product p
        JOIN p.categories c
        WHERE p.deleted = false
          AND (:productIds IS NULL OR p.id NOT IN :productIds)
          AND c IN :categories
        """)
    List<Product> findCandidateProducts(
            @Param("categories") Set<Category> categories,
            @Param("productIds") List<String> productIds,
            Pageable pageable
    );

}
