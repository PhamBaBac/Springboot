package com.bacpham.kanban_service.repository;

import com.bacpham.kanban_service.entity.Product;
import com.bacpham.kanban_service.entity.SubProduct;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubProductRepository extends JpaRepository<SubProduct, String> {

    /**
     * Fix Concurrency: Khóa bi quan (Pessimistic Write Lock - SELECT ... FOR UPDATE)
     * Đảm bảo khi nhiều transaction cùng trừ tồn kho, chỉ 1 transaction được xử lý tại 1 thời điểm.
     * Tránh triệt để Race Condition, Lost Update và Overselling.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT sp FROM SubProduct sp WHERE sp.id = :id")
    Optional<SubProduct> findByIdWithLock(@Param("id") String id);

    List<SubProduct> findAllByProductAndDeletedFalse(Product product);
    @Query("SELECT SUM(sp.stock) FROM SubProduct sp WHERE sp.product.id = :productId")
    Integer sumStockByProductId(@Param("productId") String productId);

    @Query(value = "SELECT COUNT(*) FROM sub_products WHERE qty > 0 AND deleted = false", nativeQuery = true)
    long countSubProductsWithStock();

    @Query(value = "SELECT COALESCE(SUM(qty), 0) FROM sub_products WHERE deleted = false", nativeQuery = true)
    long getTotalQty();

    @Query(value = "SELECT COALESCE(SUM(price * qty), 0) FROM sub_products WHERE deleted = false", nativeQuery = true)
    double getTotalSubProductAmount();

    @Query("SELECT DISTINCT UPPER(sp.size) FROM SubProduct sp")
    List<String> findDistinctSizes();

    @Query("SELECT DISTINCT UPPER(sp.color) FROM SubProduct sp")
    List<String> findDistinctColors();

    @Query("SELECT DISTINCT sp.price FROM SubProduct sp")
    List<Double> findDistinctPrices();

    @Query("""
        SELECT DISTINCT UPPER(sp.size) FROM SubProduct sp
        JOIN sp.product p
        JOIN p.categories c
        WHERE sp.deleted = false AND p.deleted = false
          AND (c.id IN :catIds OR c.parentId IN :catIds)
          AND (:search IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :search, '%')))
          AND sp.size IS NOT NULL AND TRIM(sp.size) != ''
    """)
    List<String> findDistinctSizesByCatIds(
            @Param("catIds") List<String> catIds,
            @Param("search") String search
    );

    @Query("""
        SELECT DISTINCT UPPER(sp.size) FROM SubProduct sp
        JOIN sp.product p
        WHERE sp.deleted = false AND p.deleted = false
          AND (:search IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :search, '%')))
          AND sp.size IS NOT NULL AND TRIM(sp.size) != ''
    """)
    List<String> findDistinctSizesWithoutCatIds(
            @Param("search") String search
    );

    @Query("""
        SELECT DISTINCT UPPER(sp.color) FROM SubProduct sp
        JOIN sp.product p
        JOIN p.categories c
        WHERE sp.deleted = false AND p.deleted = false
          AND (c.id IN :catIds OR c.parentId IN :catIds)
          AND (:search IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :search, '%')))
          AND sp.color IS NOT NULL AND TRIM(sp.color) != ''
    """)
    List<String> findDistinctColorsByCatIds(
            @Param("catIds") List<String> catIds,
            @Param("search") String search
    );

    @Query("""
        SELECT DISTINCT UPPER(sp.color) FROM SubProduct sp
        JOIN sp.product p
        WHERE sp.deleted = false AND p.deleted = false
          AND (:search IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :search, '%')))
          AND sp.color IS NOT NULL AND TRIM(sp.color) != ''
    """)
    List<String> findDistinctColorsWithoutCatIds(
            @Param("search") String search
    );

    @Query("""
        SELECT DISTINCT sp.price FROM SubProduct sp
        JOIN sp.product p
        JOIN p.categories c
        WHERE sp.deleted = false AND p.deleted = false
          AND (c.id IN :catIds OR c.parentId IN :catIds)
          AND (:search IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :search, '%')))
          AND sp.price IS NOT NULL
        ORDER BY sp.price ASC
    """)
    List<Double> findDistinctPricesByCatIds(
            @Param("catIds") List<String> catIds,
            @Param("search") String search
    );

    @Query("""
        SELECT DISTINCT sp.price FROM SubProduct sp
        JOIN sp.product p
        WHERE sp.deleted = false AND p.deleted = false
          AND (:search IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :search, '%')))
          AND sp.price IS NOT NULL
        ORDER BY sp.price ASC
    """)
    List<Double> findDistinctPricesWithoutCatIds(
            @Param("search") String search
    );

}
