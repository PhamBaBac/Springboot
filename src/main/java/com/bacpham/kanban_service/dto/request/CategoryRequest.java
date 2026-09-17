package com.bacpham.kanban_service.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@ToString
public class CategoryRequest {
    String id;
    String title;
    String slug;
    String description;
    String parentId;
}
