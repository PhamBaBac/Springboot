package com.bacpham.kanban_service.entity;

import com.bacpham.kanban_service.helper.base.model.BaseModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "medias")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Media extends BaseModel {

    @Column(name = "url", nullable = false, length = 1000)
    String url;

    @Column(name = "public_id")
    String publicId;

    @Column(name = "file_name")
    String fileName;

    @Column(name = "file_type")
    String fileType;

    @Column(name = "file_size")
    Long fileSize;

    @Column(name = "width")
    Integer width;

    @Column(name = "height")
    Integer height;

    @Column(name = "created_by")
    String createdBy;
}
