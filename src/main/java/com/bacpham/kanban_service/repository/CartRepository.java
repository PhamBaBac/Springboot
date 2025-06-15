package com.bacpham.kanban_service.repository;

import com.bacpham.kanban_service.entity.Cart;
import com.bacpham.kanban_service.entity.SubProduct;
import com.bacpham.kanban_service.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CartRepository extends JpaRepository<Cart, String> {
    List<Cart> findByCreatedBy(User user);

    Optional<Cart> findByCreatedByAndSubProduct(User user, SubProduct subProduct);

    Optional<Cart> findBySubProduct_IdAndCreatedByAndIdNot(String subProductId, User createdBy, String id);


}
