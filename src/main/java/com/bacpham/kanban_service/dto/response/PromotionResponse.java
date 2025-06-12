package com.bacpham.kanban_service.dto.response;


import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromotionResponse {
    private UUID id;
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