package com.bacpham.kanban_service.entity;

import com.bacpham.kanban_service.helper.base.model.BaseModel;
import com.bacpham.kanban_service.utils.formater.time.DateGenerator;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.Date;

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
    @JoinColumn(name = "user_id")
    User user;

    @Column(columnDefinition = "TEXT")
    String userMessage;

    @Column(columnDefinition = "TEXT")
    String aiResponse;


    @Column(name = "user_created_at", updatable = false)
    LocalDateTime userCreatedAt;

    @Column(name = "ai_created_at")
    LocalDateTime aiCreatedAt;
}

