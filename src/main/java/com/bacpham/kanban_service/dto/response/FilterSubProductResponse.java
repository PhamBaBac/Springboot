package com.bacpham.kanban_service.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@ToString
public class FilterSubProductResponse implements Serializable {
    private static final long serialVersionUID = 1L;
    String sku;
    String size;
    String color;
    double price;
    Double cost;
    double discount;
    Integer stock;
    java.util.Map<String, String> attributes;
}
