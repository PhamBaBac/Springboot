package com.bacpham.kanban_service.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class BillResponse {
    private String image;
    private String title;
    private String size;
    private int qty;
    private double price;
    private double totalPrice;
    private String status; // PENDING, PAID, COMPLETED, CANCELLED, REFUNDED

}