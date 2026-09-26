package com.bacpham.kanban_service.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.io.Serializable;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@ToString
public class ProductResponse implements Serializable {
    private static final long serialVersionUID = 1L;
    String id;
    String title;
    String slug;
    String description;
    String content;
    Boolean isDeleted;
    Set<CategoryResponse> categories;
    Set<FilterSubProductResponse> subProducts;
    String supplierId;
    Set<String> images;
}
