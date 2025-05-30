package com.bacpham.kanban_service.repository;

import com.bacpham.kanban_service.entity.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
public interface SupplierRepository  extends JpaRepository<Supplier, String> {

    Page<Supplier> findAllByDeletedFalse(Pageable pageable);

    @Query("SELECT s FROM Supplier s " +
            "WHERE (:start IS NULL OR s.createdAt >= :start) " +
            "AND (:end IS NULL OR s.createdAt <= :end)")
    List<Supplier> findByCreatedAtBetweenOptional(@Param("start") Date start, @Param("end") Date end);

}
