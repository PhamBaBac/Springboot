package com.bacpham.kanban_service.entity;

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
    public class Category {

        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        String id;

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

        @Column(nullable = false)
        Boolean deleted = false;

        @Temporal(TemporalType.TIMESTAMP)
        @Column(nullable = false, updatable = false)
        Date createdAt;

        @Temporal(TemporalType.TIMESTAMP)
        @Column(nullable = false)
        Date updatedAt;

        @PrePersist
        protected void onCreate() {
            Date now = new Date();
            this.createdAt = now;
            this.updatedAt = now;
        }

        @PreUpdate
        protected void onUpdate() {
            this.updatedAt = new Date();
        }
}
