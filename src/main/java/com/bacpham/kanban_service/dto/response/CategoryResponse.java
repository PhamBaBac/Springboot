package com.bacpham.kanban_service.dto.response;

import lombok.*;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryResponse {
    private String id;
    private String title;
    private String slug;
    private String description;
    private String parentId;
}
