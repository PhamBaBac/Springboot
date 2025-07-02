package com.bacpham.kanban_service.dto.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class OrderItemRequest {
    private String subProductId;
    private Integer count;
    private Double price;
    private String addressId; // Address ID for shipping
}
