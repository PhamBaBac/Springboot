package com.bacpham.kanban_service.dto.request;


import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReviewProductRequest {
    String createdBy; // userId
    String subProductId;
    String OrderId;
    String comment;
    Integer star;
    List<String> images;
}