package com.bacpham.kanban_service.repository;

import com.bacpham.kanban_service.entity.Media;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MediaRepository extends JpaRepository<Media, String>, JpaSpecificationExecutor<Media> {

    Page<Media> findAllByDeletedFalse(Pageable pageable);

    Page<Media> findByFileNameContainingIgnoreCaseAndDeletedFalse(String fileName, Pageable pageable);

    Optional<Media> findByUrlAndDeletedFalse(String url);

    boolean existsByUrlAndDeletedFalse(String url);
}
