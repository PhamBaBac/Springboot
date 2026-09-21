package com.bacpham.kanban_service.repository;

import com.bacpham.kanban_service.entity.Shipment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShipmentRepository extends JpaRepository<Shipment, String> {
    List<Shipment> findByOrderId(String orderId);
    Optional<Shipment> findByTrackingCode(String trackingCode);
    Optional<Shipment> findByShipmentCode(String shipmentCode);

    @Query("SELECT s FROM Shipment s WHERE " +
           "(:status IS NULL OR s.shippingStatus = :status) AND " +
           "(:search IS NULL OR LOWER(s.shipmentCode) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(s.trackingCode) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(s.order.id) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY s.createdAt DESC")
    Page<Shipment> findShipmentsWithFilter(
            @Param("status") String status,
            @Param("search") String search,
            Pageable pageable
    );
}
