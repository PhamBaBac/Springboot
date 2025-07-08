package com.bacpham.kanban_service.dto.response;

import com.bacpham.kanban_service.enums.OrderStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StatisticsOrderResponse {
    String orderId;
    String subProductId;
    String image;
    String title;
    String size;
    int qty;
    double price;
    double cost;
    double discount;
    double totalPrice;
    OrderStatus orderStatus;
    Boolean isReviewed;
}
