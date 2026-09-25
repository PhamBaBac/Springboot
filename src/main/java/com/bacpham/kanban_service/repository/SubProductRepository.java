package com.bacpham.kanban_service.repository;

import com.bacpham.kanban_service.entity.Product;
import com.bacpham.kanban_service.entity.SubProduct;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubProductRepository extends JpaRepository<SubProduct, String> {

    /**
     * Khóa bi quan (Pessimistic Write Lock - SELECT ... FOR UPDATE)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT sp FROM SubProduct sp WHERE sp.id = :id")
    Optional<SubProduct> findByIdWithLock(@Param("id") String id);

    /**
     * Atomic Stock Reservation: Tạm giữ tồn kho nguyên tử.
     * Tăng reservedStock trong 1 câu UPDATE nguyên tử.
     * Điều kiện: (stock - reservedStock) >= :quantity.
     * Trả về 1 nếu thành công, 0 nếu không đủ hàng hoặc bị xóa.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE SubProduct sp " +
           "SET sp.reservedStock = COALESCE(sp.reservedStock, 0) + :quantity " +
           "WHERE sp.id = :id AND sp.deleted = false " +
           "AND (COALESCE(sp.stock, 0) - COALESCE(sp.reservedStock, 0)) >= :quantity")
    int reserveStock(@Param("id") String id, @Param("quantity") int quantity);

    /**
     * Atomic Release Reserved Stock: Giải phóng tồn kho giữ chân.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE SubProduct sp " +
           "SET sp.reservedStock = CASE WHEN COALESCE(sp.reservedStock, 0) < :quantity THEN 0 " +
           "                           ELSE COALESCE(sp.reservedStock, 0) - :quantity END " +
           "WHERE sp.id = :id")
    int releaseReservedStock(@Param("id") String id, @Param("quantity") int quantity);

    /**
     * Atomic Deduct Stock on Shipment: Trừ tồn kho vật lý và tồn kho giữ chân khi xuất kho giao shipper.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE SubProduct sp " +
           "SET sp.stock = CASE WHEN COALESCE(sp.stock, 0) < :quantity THEN 0 " +
           "                    ELSE COALESCE(sp.stock, 0) - :quantity END, " +
           "    sp.qty = CASE WHEN COALESCE(sp.qty, 0) < :quantity THEN 0 " +
           "                  ELSE COALESCE(sp.qty, 0) - :quantity END, " +
           "    sp.reservedStock = CASE WHEN COALESCE(sp.reservedStock, 0) < :quantity THEN 0 " +
           "                           ELSE COALESCE(sp.reservedStock, 0) - :quantity END " +
           "WHERE sp.id = :id")
    int deductStockOnShipment(@Param("id") String id, @Param("quantity") int quantity);

    /**
     * Atomic Direct Deduct: Khấu trừ trực tiếp tồn kho (Stock & Qty) nguyên tử.
     * Tránh triệt để Race Condition / Over-selling mà không cần giữ connection lock dài.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE SubProduct sp " +
           "SET sp.stock = sp.stock - :quantity, " +
           "    sp.qty = CASE WHEN (COALESCE(sp.qty, 0) - :quantity) < 0 THEN 0 ELSE (COALESCE(sp.qty, 0) - :quantity) END " +
           "WHERE sp.id = :id AND sp.deleted = false AND COALESCE(sp.stock, 0) >= :quantity")
    int directDeductStock(@Param("id") String id, @Param("quantity") int quantity);

    /**
     * Atomic Direct Restock: Hoàn trả số lượng tồn kho nguyên tử.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE SubProduct sp " +
           "SET sp.stock = COALESCE(sp.stock, 0) + :quantity, " +
           "    sp.qty = COALESCE(sp.qty, 0) + :quantity " +
           "WHERE sp.id = :id")
    int directRestock(@Param("id") String id, @Param("quantity") int quantity);

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
