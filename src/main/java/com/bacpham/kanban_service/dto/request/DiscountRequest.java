package com.bacpham.kanban_service.dto.request;

import com.bacpham.kanban_service.enums.PromotionType;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@ToString
public class DiscountRequest {
    String value;
    PromotionType type;
}
