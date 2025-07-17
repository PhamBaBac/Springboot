package com.bacpham.kanban_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SubProductSellingInfo {
    private String title;
    private String color;
    private String size;
    private Long soldQuantity;
    private Integer stock;
    private Double price;
    private List<String> images;
}


