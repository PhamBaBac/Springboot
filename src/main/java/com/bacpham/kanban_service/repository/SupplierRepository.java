package com.bacpham.kanban_service.repository;

import com.bacpham.kanban_service.entity.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SupplierRepository  extends JpaRepository<Supplier, String> {

    Page<Supplier> findAllByDeletedFalse(Pageable pageable);

}
