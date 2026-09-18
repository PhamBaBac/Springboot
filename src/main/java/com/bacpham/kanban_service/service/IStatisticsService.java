package com.bacpham.kanban_service.service;

import com.bacpham.kanban_service.dto.response.StatisticsResponse;
import com.bacpham.kanban_service.dto.response.StatisticsTopSellingLowQuantityResponse;

import java.util.List;
import java.util.Map;

/**
 * Interface cho Statistics Service.
 * Tuan thu Dependency Inversion Principle: cac class phu thuoc nen
 * phu thuoc vao abstraction (interface) nay, khong phai StatisticsService concrete class.
 */
public interface IStatisticsService {

    /**
     * Tra ve tong quan thong ke he thong: so supplier, product, order, doanh thu.
     */
    StatisticsResponse getStatistics();

    /**
     * Tra ve top san pham ban chay va san pham sap het hang.
     */
    StatisticsTopSellingLowQuantityResponse getTopSellingAndLowQuantity();

    /**
     * Tra ve thong ke don hang theo thoi gian (weekly/monthly/yearly).
     * Business logic nay duoc chuyen tu Controller vao Service de tuan thu SRP.
     * @param timeType kieu phan nhom: "weekly", "monthly", "yearly"
     */
    List<Map<String, Object>> getOrderPurchaseStatistics(String timeType);
}
