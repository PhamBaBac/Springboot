package com.bacpham.kanban_service.repository;

import com.bacpham.kanban_service.entity.Bill;
import com.bacpham.kanban_service.entity.ChatHistory;
import com.bacpham.kanban_service.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatHistoryRepository extends JpaRepository<ChatHistory, String> {
    List<ChatHistory> findByUserOrderByCreatedAtDesc(User user);

}


