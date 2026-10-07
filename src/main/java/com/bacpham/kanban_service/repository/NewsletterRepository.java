package com.bacpham.kanban_service.repository;

import com.bacpham.kanban_service.entity.Newsletter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NewsletterRepository extends JpaRepository<Newsletter, String> {
    boolean existsByEmail(String email);
    Optional<Newsletter> findByEmail(String email);
}
