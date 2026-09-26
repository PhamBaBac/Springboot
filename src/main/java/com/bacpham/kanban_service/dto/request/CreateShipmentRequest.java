package com.bacpham.kanban_service.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateShipmentRequest {

    @NotBlank(message = "Mã đơn hàng không được để trống")
    String orderId;

    @NotNull(message = "Cân nặng không được để trống")
    @Min(value = 10, message = "Cân nặng tối thiểu là 10 gram")
    Integer weight; 

    @NotNull(message = "Chiều dài không được để trống")
    @Min(value = 1, message = "Chiều dài tối thiểu là 1 cm")
    Integer length; 

    @NotNull(message = "Chiều rộng không được để trống")
    @Min(value = 1, message = "Chiều rộng tối thiểu là 1 cm")
    Integer width; 

    @NotNull(message = "Chiều cao không được để trống")
    @Min(value = 1, message = "Chiều cao tối thiểu là 1 cm")
    Integer height; 

    Double codAmount; 

    String note;

    @Builder.Default
    String requiredNote = "CHOXEMHANGKHONGTHU";

    @NotEmpty(message = "Kiện hàng phải có ít nhất 1 sản phẩm")
    List<ShipmentItemPackRequest> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShipmentItemPackRequest {
        @NotBlank(message = "orderItemId không được để trống")
        String orderItemId;

        @NotNull(message = "Số lượng không được để trống")
        @Min(value = 1, message = "Số lượng tối thiểu là 1")
        Integer quantity;
    }
}
