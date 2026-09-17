package com.bacpham.kanban_service.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class CartResponse {
    private String id;
    private String createdBy;
    private String subProductId;
    private String size;
    private String color;
    private String title;
    private Double price;
    private Integer qty;
    private Integer count;
    private String productId;
    private String image;
    private Integer stock;
    private Boolean isDeleted;
}
