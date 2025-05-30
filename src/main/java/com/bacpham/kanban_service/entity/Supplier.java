package com.bacpham.kanban_service.entity;

import com.bacpham.kanban_service.helper.base.model.BaseModel;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
public class Supplier extends BaseModel {
    @Column(nullable = false)
    String name;

    String slug;

    String contact;

    String email;

    String photoUrl;

    @OneToMany(mappedBy = "supplier", cascade = CascadeType.ALL)
    List<Category> categories = new ArrayList<>();

    Double price;

    @Column(columnDefinition = "TINYINT DEFAULT 0")
    Integer isTaking; // 0 or 1

    Integer active;

    @Column(nullable = false)
    boolean deleted = false;

    @OneToMany(mappedBy = "supplier", cascade = CascadeType.ALL)
    List<Product> products = new ArrayList<>();

}
