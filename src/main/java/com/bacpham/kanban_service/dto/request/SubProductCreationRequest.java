package com.bacpham.kanban_service.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SubProductCreationRequest {
    String id;
    String productId;
    String size;
    String color;
    double price;
    double discount;
    int qty;
    double cost;
    Set<String> images;
}
