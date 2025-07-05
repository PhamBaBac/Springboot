package com.bacpham.kanban_service.dto.response;

import com.bacpham.kanban_service.enums.OrderStatus;
import com.bacpham.kanban_service.enums.PaymentType;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class OrderResponse {
    private String orderId;
    private String subProductId;
    private String image;
    private String title;
    private String size;
    private int qty;
    private double price;
    private double totalPrice;
    private OrderStatus orderStatus;
    private Boolean isReviewed;

}
