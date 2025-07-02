package com.bacpham.kanban_service.dto.response;
import com.bacpham.kanban_service.enums.BillStatus;
import com.bacpham.kanban_service.enums.OrderStatus;
import com.bacpham.kanban_service.enums.PaymentStatus;
import com.bacpham.kanban_service.enums.PaymentType;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class OrderDetailResponse {
    private String id;
    private String userName; // ten nguoi tao hoa don
    private String nameRecipient; //ten nguoi nhan
    private String address;
    private String phoneNumber;
    private String email;
    private PaymentType paymentType;
    private OrderStatus orderStatus;
    private List<OrderResponse> orderResponses;
    private LocalDate createdAt;
}
