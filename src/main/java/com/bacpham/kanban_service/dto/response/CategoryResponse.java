package com.bacpham.kanban_service.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class CategoryResponse {
    private String id;
    private String title;
    private String slug;
    private String description;
    private String parentId;
}
