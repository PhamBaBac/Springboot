package com.bacpham.kanban_service.service;

import com.bacpham.kanban_service.dto.request.CalculateShippingFeeRequest;
import com.bacpham.kanban_service.dto.request.CreateShipmentRequest;
import com.bacpham.kanban_service.dto.response.ShipmentResponse;

import java.util.List;

public interface IShipmentService {
    ShipmentResponse createShipment(CreateShipmentRequest request);
    List<ShipmentResponse> getShipmentsByOrderId(String orderId);
    com.bacpham.kanban_service.dto.response.PageResponse<ShipmentResponse> getShipmentsPage(int page, int pageSize, String status, String search);
    ShipmentResponse getShipmentById(String shipmentId);
    Double calculateShippingFee(CalculateShippingFeeRequest request);
}
