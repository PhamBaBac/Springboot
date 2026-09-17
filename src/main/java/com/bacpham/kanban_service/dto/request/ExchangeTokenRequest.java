package com.bacpham.kanban_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExchangeTokenRequest {
    @NotBlank(message = "Exchange code must not be blank")
    private String code;
}
