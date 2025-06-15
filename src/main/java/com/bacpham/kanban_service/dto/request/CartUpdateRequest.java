package com.bacpham.kanban_service.dto.request;

import lombok.Data;

@Data
public class CartUpdateRequest {
    private String createdBy;
    private String subProductId;
    private Integer count;
    private String size;
    private String color;
    private Double price;
    private Integer qty;
    private String productId;
    private String image;
    private String title;
}
