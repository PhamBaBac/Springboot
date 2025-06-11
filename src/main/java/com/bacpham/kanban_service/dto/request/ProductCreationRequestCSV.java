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
public class ProductCreationRequestCSV {
    String title;
    String slug;
    String description;
    String content;

    String categoryNames;

    String supplierName;

    String images;

}
