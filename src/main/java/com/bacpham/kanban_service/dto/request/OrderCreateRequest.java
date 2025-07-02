package com.bacpham.kanban_service.dto.request;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderCreateRequest {
    private String addressId;
    private List<OrderItemRequest> items;
}