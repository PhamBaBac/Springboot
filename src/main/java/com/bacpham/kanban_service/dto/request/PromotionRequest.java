package com.bacpham.kanban_service.dto.request;


import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromotionRequest {
    private String title;
    private String description;
    private String code;
    private Double value;
    private Integer numOfAvailable;
    private String type; // "discount" | "percent"
    private String imageURL;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
}