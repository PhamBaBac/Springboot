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
    Double price;
    Double discount;
    Integer qty;
    Integer cost;
    Set<String> images;
}
