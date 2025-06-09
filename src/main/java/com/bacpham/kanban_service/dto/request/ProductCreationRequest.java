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
@ToString
public class ProductCreationRequest {
    String title;
    String slug;
    String description;
    String content;
    Set<String> categories;
    String supplierId;
    Set<SubProductCreationRequest> subProducts;
    Set<String> images;
}

