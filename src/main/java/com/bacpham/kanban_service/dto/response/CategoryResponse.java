package com.bacpham.kanban_service.dto.response;

import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class CategoryResponse implements Serializable {
    private static final long serialVersionUID = 1L;
    private String id;
    private String title;
    private String slug;
    private String description;
    private String parentId;
}
