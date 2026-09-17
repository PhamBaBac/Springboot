package com.bacpham.kanban_service.dto.request;

import com.bacpham.kanban_service.enums.OrderStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@ToString
public class UpdateStatusOrder {
    OrderStatus orderStatus;
    String cancelReason;
    String trackingCode;
}
