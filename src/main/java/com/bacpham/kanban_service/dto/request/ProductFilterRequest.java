package com.bacpham.kanban_service.dto.request;

import com.fasterxml.jackson.annotation.JsonPropertyDescription; // <-- THAY ĐỔI Ở ĐÂY
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductFilterRequest {
    @JsonPropertyDescription("Danh sách các từ khóa có trong tên sản phẩm. Ví dụ: ['áo thun', 'quần jean']")
    private List<String> names;

    @JsonPropertyDescription("Danh sách các kích cỡ (size) sản phẩm. Ví dụ: ['S', 'M', 'L']")
    private List<String> sizes;

    @JsonPropertyDescription("Mức giá tối thiểu của sản phẩm")
    private Double minPrice;

    @JsonPropertyDescription("Mức giá tối đa của sản phẩm")
    private Double maxPrice;
}