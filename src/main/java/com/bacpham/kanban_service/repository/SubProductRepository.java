package com.bacpham.kanban_service.repository;

import com.bacpham.kanban_service.entity.Product;
import com.bacpham.kanban_service.entity.SubProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubProductRepository extends JpaRepository<SubProduct, String> {

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

}
