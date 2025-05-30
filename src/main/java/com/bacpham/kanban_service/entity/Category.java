package com.bacpham.kanban_service.entity;

import com.bacpham.kanban_service.helper.base.model.BaseModel;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
    public class Category extends BaseModel {
        @Column(nullable = false, unique = true, length = 255)
        String title;

        String slug;

        @Column(columnDefinition = "TEXT")
        String description;

        @Column(name = "parent_id")
        String parentId;

        @ManyToOne
        @JoinColumn(name = "supplier_id")
        private Supplier supplier;
}
