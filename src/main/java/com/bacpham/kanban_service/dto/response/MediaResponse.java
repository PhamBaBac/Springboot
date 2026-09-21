package com.bacpham.kanban_service.dto.response;

import com.bacpham.kanban_service.utils.formater.time.DateGenerator;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MediaResponse {

    String id;
    String url;
    String publicId;
    String fileName;
    String fileType;
    Long fileSize;
    Integer width;
    Integer height;
    String createdBy;

    @JsonSerialize(using = DateGenerator.class)
    Date createdAt;

    @JsonSerialize(using = DateGenerator.class)
    Date updatedAt;
}
