package com.bacpham.kanban_service.entity;

import com.bacpham.kanban_service.enums.ActionType;
import com.bacpham.kanban_service.helper.base.model.BaseModel;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@Table(name = "user_activity")
public class UserActivity extends BaseModel {

    @ManyToOne(fetch = FetchType.LAZY) // Dùng LAZY để tối ưu hiệu năng
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    ActionType actionType;

    // Lưu ID của Product hoặc SubProduct
    @Column(name = "entity_id")
    String entityId;

    @Column(name = "search_query")
    String searchQuery;
}