package com.bacpham.kanban_service.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@ToString
public class SupplierRequest {
    String id;
    String name;
    String slug;
    String contact;
    String email;
    String photoUrl;
    List<String> categories;
    Double price;
    Integer isTaking; // 0 or 1
    Integer active;
    Boolean deleted;
}