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

public class ProductResponse {
    String id;
    String title;
    String slug;
    String description;
    String content;
    boolean isDeleted;
    Set<CategoryResponse> categories;
    String supplierId;
    Set<SubProductResponse> subProducts;
    Set<String> images;
}
