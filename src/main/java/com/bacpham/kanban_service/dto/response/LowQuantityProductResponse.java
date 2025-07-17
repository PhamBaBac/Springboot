package com.bacpham.kanban_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@Builder
public class LowQuantityProductResponse {
    private String name;
    private Long remainingQuantity;
    private List<String> images;
    public LowQuantityProductResponse(String name, Long remainingQuantity, List<String> images) {
        this.name = name;
        this.remainingQuantity = remainingQuantity;
        this.images = images;
    }
}
