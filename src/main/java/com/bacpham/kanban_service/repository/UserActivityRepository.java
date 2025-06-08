package com.bacpham.kanban_service.repository;

import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.entity.UserActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserActivityRepository extends JpaRepository<UserActivity, String> { // ID của BaseModel là String

    List<UserActivity> findTop10ByUserOrderByCreatedAtDesc(User user);
}

