package com.bacpham.kanban_service.repository;

import com.bacpham.kanban_service.entity.ShipmentItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShipmentItemRepository extends JpaRepository<ShipmentItem, String> {
    List<ShipmentItem> findByShipmentId(String shipmentId);
}
