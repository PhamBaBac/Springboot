package com.bacpham.kanban_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SubProductSellingInfo {
    private String name;           // Tên subProduct hoặc product
    private int soldQuantity;      // Số lượng đã bán
    private int remainingQuantity; // Số lượng còn lại (stock)
    private double price;
}

