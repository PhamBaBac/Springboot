package com.bacpham.kanban_service.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Date;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ShipmentResponse {
    String id;
    String orderId;
    String shipmentCode;
    String carrier;
    String trackingCode;
    String shippingStatus;
    String shippingStatusName;
    Integer weight;
    Integer length;
    Integer width;
    Integer height;
    Double codAmount;
    Double shippingFee;
    String note;
    String requiredNote;
    Date pickedDate;
    Date deliveredDate;
    Date createdAt;
    List<ShipmentItemResponse> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShipmentItemResponse {
        String id;
        String orderItemId;
        String productTitle;
        String variantName;
        Integer quantity;
        Double price;
    }
}
