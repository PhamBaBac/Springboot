package com.bacpham.kanban_service.dto.response;

import com.bacpham.kanban_service.dto.request.SubProductCreationRequest;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductAiResponse {
    String id;
    String title;
    Set<String> images;
}
