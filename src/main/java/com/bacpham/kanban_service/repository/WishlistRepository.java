package com.bacpham.kanban_service.repository;

import com.bacpham.kanban_service.entity.Product;
import com.bacpham.kanban_service.entity.Wishlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WishlistRepository extends JpaRepository<Wishlist, String> {

    List<Wishlist> findByUserIdOrderByCreatedAtDesc(String userId);

    List<Wishlist> findByUserEmailOrderByCreatedAtDesc(String email);

    Optional<Wishlist> findByUserIdAndProductId(String userId, String productId);

    Optional<Wishlist> findByUserEmailAndProductId(String email, String productId);

    boolean existsByUserIdAndProductId(String userId, String productId);

    boolean existsByUserEmailAndProductId(String email, String productId);

    @Modifying
    @Query("DELETE FROM Wishlist w WHERE w.user.id = :userId AND w.product.id = :productId")
    void deleteByUserIdAndProductId(@Param("userId") String userId, @Param("productId") String productId);

    @Modifying
    @Query("DELETE FROM Wishlist w WHERE w.user.email = :email AND w.product.id = :productId")
    void deleteByUserEmailAndProductId(@Param("email") String email, @Param("productId") String productId);

    @Modifying
    @Query("DELETE FROM Wishlist w WHERE w.user.email = :email")
    void deleteByUserEmail(@Param("email") String email);

    @Query("SELECT w.product.id FROM Wishlist w WHERE w.user.id = :userId")
    List<String> findProductIdsByUserId(@Param("userId") String userId);

    @Query("SELECT w.product.id FROM Wishlist w WHERE w.user.email = :email")
    List<String> findProductIdsByUserEmail(@Param("email") String email);

    @Query("SELECT w.product FROM Wishlist w WHERE w.user.email = :email ORDER BY w.createdAt DESC")
    List<Product> findProductsByUserEmail(@Param("email") String email);

    @Query("SELECT COUNT(w) FROM Wishlist w WHERE w.product.id = :productId")
    long countByProductId(@Param("productId") String productId);
}
