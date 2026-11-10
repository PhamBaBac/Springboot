package com.bacpham.kanban_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class CartCreateRequest {
    private String createdBy;

    @NotBlank(message = "Mã biến thể sản phẩm (subProductId) không được để trống")
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
