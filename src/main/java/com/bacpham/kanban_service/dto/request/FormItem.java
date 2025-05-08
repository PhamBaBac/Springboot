package com.bacpham.kanban_service.dto.request;


import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FormItem {
    String key;
    String value;
    String label;
    String placeholder;
    String type;
    Boolean required;
    String message;
     String default_value;
     Integer displayLength;
    List<String> lookup_items;
}
