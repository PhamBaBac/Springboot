package com.bacpham.kanban_service.entity;

import com.bacpham.kanban_service.dto.response.ProductResponse;
import com.bacpham.kanban_service.helper.base.model.BaseModel;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(name = "chat_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class ChatHistory extends BaseModel {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    @Column(name = "role", nullable = false)
    String role;

    @Column(name = "message", columnDefinition = "TEXT")
    String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "products", columnDefinition = "JSON")
    List<ProductResponse> products;
}