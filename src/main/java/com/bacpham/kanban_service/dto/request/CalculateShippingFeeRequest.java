package com.bacpham.kanban_service.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CalculateShippingFeeRequest {
    String orderId;

    Integer toDistrictId;
    String toWardCode;

    @NotNull(message = "Cân nặng không được để trống")
    @Min(value = 10, message = "Cân nặng tối thiểu 10 gram")
    Integer weight;

    Integer length;
    Integer width;
    Integer height;

    Double insuranceValue;
}
