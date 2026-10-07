package com.bacpham.kanban_service.entity;

import com.bacpham.kanban_service.helper.base.model.BaseModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@Table(name = "newsletters")
public class Newsletter extends BaseModel {

    @Column(nullable = false, unique = true)
    String email;

    @Builder.Default
    Boolean active = true;
}
