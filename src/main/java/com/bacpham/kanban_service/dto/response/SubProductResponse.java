package com.bacpham.kanban_service.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Map;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SubProductResponse {
    String id;
    String size;
    String color;
    Double price;
    Double discount;
    Integer stock;
    Integer qty;
    Double cost;
    Map<String, String> attributes;
    Set<String> images;
}
