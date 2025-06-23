package com.bacpham.kanban_service.repository;

import com.bacpham.kanban_service.entity.Category;
import com.bacpham.kanban_service.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface ProductRepository extends JpaRepository<Product, String> {

    Page<Product> findByTitleContainingIgnoreCase(String title, Pageable pageable);

    Page<Product> findAllByDeletedFalse(Pageable pageable);

    @Query(value = """
   SELECT DISTINCT p FROM Product p
   JOIN p.categories c
   LEFT JOIN p.subProducts sp
   WHERE p.deleted = false
     AND (:categoryIds IS NULL OR c.id IN :categoryIds)
     AND (:sizes IS NULL OR sp.size IN :sizes)
     AND (:colors IS NULL OR sp.color IN :colors)
     AND (:minPrice IS NULL OR sp.price >= :minPrice)
     AND (:maxPrice IS NULL OR sp.price <= :maxPrice)
""",
            countQuery = """
   SELECT COUNT(DISTINCT p.id) FROM Product p
   JOIN p.categories c
   LEFT JOIN p.subProducts sp
   WHERE p.deleted = false
     AND (:categoryIds IS NULL OR c.id IN :categoryIds)
     AND (:sizes IS NULL OR sp.size IN :sizes)
     AND (:colors IS NULL OR sp.color IN :colors)
     AND (:minPrice IS NULL OR sp.price >= :minPrice)
     AND (:maxPrice IS NULL OR sp.price <= :maxPrice)
""")
    Page<Product> findFilteredProducts(
            @Param("categoryIds") List<String> categoryIds,
            @Param("sizes") List<String> sizes,
            @Param("colors") List<String> colors,
            @Param("minPrice") Double minPrice,
            @Param("maxPrice") Double maxPrice,
            Pageable pageable
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