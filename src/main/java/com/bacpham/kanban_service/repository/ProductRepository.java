package com.bacpham.kanban_service.repository;

import com.bacpham.kanban_service.dto.response.LowQuantityProductResponse;
import com.bacpham.kanban_service.entity.Category;
import com.bacpham.kanban_service.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface ProductRepository extends JpaRepository<Product, String>, JpaSpecificationExecutor<Product> {

    Page<Product> findByTitleContainingIgnoreCase(String title, Pageable pageable);

    Page<Product> findAllByDeletedFalse(Pageable pageable);

    /** Đếm tổng số product không bị xóa — dùng cho 2-query pagination pattern */
    long countByDeletedFalse();

    /**
     * Fix N+1-3: Lấy danh sách product IDs trước (paginated), sau đó batch fetch associations.
     * Tránh "HHH90003004: firstResult/maxResults specified with collection fetch" exception.
     * Pattern: 2 queries = 1 COUNT + 1 IN(...) thay vì N+1 lazy queries.
     */
    @Query("""
        SELECT p.id FROM Product p
        WHERE p.deleted = false
        ORDER BY p.createdAt DESC
    """)
    List<String> findIdsByDeletedFalseOrdered(Pageable pageable);

    /**
     * Batch fetch products với đầy đủ associations bằng ID list.
     * Dùng sau khi đã paginate bằng ID — tránh N+1 lazy load.
     */
    @Query("""
        SELECT DISTINCT p FROM Product p
        LEFT JOIN FETCH p.subProducts sp
        LEFT JOIN FETCH p.categories c
        LEFT JOIN FETCH p.supplier s
        WHERE p.id IN :ids
    """)
    List<Product> findByIdsWithAssociations(@Param("ids") List<String> ids);

    @Query(value = """
   SELECT DISTINCT p FROM Product p
   JOIN p.categories c
   LEFT JOIN p.subProducts sp
   WHERE p.deleted = false
     AND (:categoryIds IS NULL OR c.id IN :categoryIds OR c.parentId IN :categoryIds)
     AND (:search IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :search, '%')))
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
     AND (:categoryIds IS NULL OR c.id IN :categoryIds OR c.parentId IN :categoryIds)
     AND (:search IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :search, '%')))
     AND (:sizes IS NULL OR sp.size IN :sizes)
     AND (:colors IS NULL OR sp.color IN :colors)
     AND (:minPrice IS NULL OR sp.price >= :minPrice)
     AND (:maxPrice IS NULL OR sp.price <= :maxPrice)
""")
    Page<Product> findFilteredProducts(
            @Param("categoryIds") List<String> categoryIds,
            @Param("search") String search,
            @Param("sizes") List<String> sizes,
            @Param("colors") List<String> colors,
            @Param("minPrice") Double minPrice,
            @Param("maxPrice") Double maxPrice,
            Pageable pageable
    );

    @Query("""
   SELECT DISTINCT p FROM Product p
   LEFT JOIN p.subProducts sp
   WHERE p.deleted = false AND (sp.stock > 0 OR sp.stock IS NULL)
     AND (:name IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :name, '%')))
     AND (:sizes IS NULL OR sp.size IN :sizes)
     AND (:minPrice IS NULL OR sp.price >= :minPrice)
     AND (:maxPrice IS NULL OR sp.price <= :maxPrice)
""")
    List<Product> findByNameLike(
            @Param("name") String name,
            @Param("sizes") List<String> sizes,
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

    @Query("""
        SELECT DISTINCT p FROM Product p
        JOIN p.categories c
        WHERE p.deleted = false
          AND p.id <> :productId
          AND c.id IN :categoryIds
        """)
    List<Product> findRelatedCandidates(
            @Param("categoryIds") java.util.Collection<String> categoryIds,
            @Param("productId") String productId,
            Pageable pageable
    );

    @Query("""
    SELECT new com.bacpham.kanban_service.dto.response.LowQuantityProductResponse(
        p.title,
        SUM(sp.stock),
        p.images
    )
    FROM Product p
    JOIN p.subProducts sp
    WHERE p.deleted = false
    GROUP BY p.id, p.title, p.images
    HAVING SUM(sp.stock) < 30
    ORDER BY SUM(sp.stock) ASC
""")
    List<LowQuantityProductResponse> findLowQuantityProducts();

}