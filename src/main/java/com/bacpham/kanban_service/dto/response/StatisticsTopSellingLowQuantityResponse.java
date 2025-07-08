package com.bacpham.kanban_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StatisticsTopSellingLowQuantityResponse {
    private List<SubProductSellingInfo> topSelling;
    private List<LowQuantityProductResponse> lowQuantity;
}
