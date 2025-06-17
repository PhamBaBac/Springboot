package com.bacpham.kanban_service.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentResponse {
    private String paymentUrl;

}
