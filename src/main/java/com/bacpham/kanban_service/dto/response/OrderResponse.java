package com.bacpham.kanban_service.dto.response;

import com.bacpham.kanban_service.enums.OrderStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class OrderResponse {
    private String orderItemId;
    private String orderId;
    private String subProductId;
    private String image;
    private String title;
    private String size;
    private String color;
    private Map<String, String> attributes;
    private int qty;
    private double price;
    private double totalPrice;
    private OrderStatus orderStatus;
    private String trackingCode;
    private Boolean isReviewed;
}
