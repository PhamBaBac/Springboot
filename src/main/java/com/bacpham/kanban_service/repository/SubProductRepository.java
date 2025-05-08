package com.bacpham.kanban_service.repository;

import com.bacpham.kanban_service.dto.request.SubProductCreationRequest;
import com.bacpham.kanban_service.entity.Category;
import com.bacpham.kanban_service.entity.SubProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Set;

@Repository
public interface SubProductRepository extends JpaRepository<SubProduct, String> {

}
