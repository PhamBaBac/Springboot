package com.bacpham.kanban_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MediaRequest {

    @NotBlank(message = "URL không được để trống")
    String url;

    String publicId;
    String fileName;
    String fileType;
    Long fileSize;
    Integer width;
    Integer height;
}
