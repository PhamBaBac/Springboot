package com.bacpham.kanban_service.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@ToString
public class FilterSubProductResponse {
    String size;
    String color;
    double price;
    double discount;
    Integer stock;
    java.util.Map<String, String> attributes;
}
