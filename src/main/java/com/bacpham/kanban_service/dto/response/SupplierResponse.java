package com.bacpham.kanban_service.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Date;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SupplierResponse {
    String id;
    String name;
    String slug;
    String contact;
    String email;
    String photoUrl;
    List<String> categories;
    List<String> products;
    Double price;
    Integer isTaking;
    Integer active;
    Boolean deleted;
}