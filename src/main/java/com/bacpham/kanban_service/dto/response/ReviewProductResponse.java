package com.bacpham.kanban_service.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReviewProductResponse {
    String id;
    String createdBy; // userId
    String userFirstname;
    String userLastname;
    String userAvatar;
    String subProductId;
    String color;
    String size;
    String comment;
    Integer star;
    List<String> images;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;


}