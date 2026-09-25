package com.bacpham.kanban_service.dto.response;

import com.bacpham.kanban_service.enums.OrderStatus;
import com.bacpham.kanban_service.utils.formater.time.DateGenerator;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Date;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderStatusHistoryResponse {
    String id;
    String orderId;
    OrderStatus fromStatus;
    OrderStatus toStatus;
    String changedById;
    String changedByRole;
    String reason;
    String metadata;

    @JsonSerialize(using = DateGenerator.class)
    Date createdAt;
}
