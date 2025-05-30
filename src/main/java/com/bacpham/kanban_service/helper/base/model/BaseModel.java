package com.bacpham.kanban_service.helper.base.model;




import com.bacpham.kanban_service.utils.formater.time.DateGenerator;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.util.Date;
import java.util.UUID;

@MappedSuperclass
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
public class BaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "Id", columnDefinition = "uuid", updatable = false, nullable = false)
    String id;

    @JsonSerialize(using = DateGenerator.class)
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    Date createdAt;

    @JsonSerialize(using = DateGenerator.class)
    @UpdateTimestamp
    @Column(name = "updated_at")
    Date updatedAt;

    @Column(name = "deleted", columnDefinition = "boolean default false")
    Boolean deleted = false;
}
