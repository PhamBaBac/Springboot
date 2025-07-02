package com.bacpham.kanban_service.repository;

import com.bacpham.kanban_service.entity.Order;
import com.bacpham.kanban_service.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
@Repository
public interface OrderRepository extends JpaRepository<Order, String> {
    List<Order> findByUser(User user);
}
