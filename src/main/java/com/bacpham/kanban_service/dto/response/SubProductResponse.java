package com.bacpham.kanban_service.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

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
    double price;
    double discount;
    int qty;
    double cost;
    Set<String> images;
}
