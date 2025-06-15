package com.bacpham.kanban_service.dto.request;

import lombok.*;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class CartCreateRequest {
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
}
