package com.bacpham.kanban_service.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StatisticsResponse {
    List<StatisticsOrderResponse> sales;
    long suppliers;
    long products;
    long orders;
    double totalOrder;
    double totalQty;
    long subProduct;
    double totalSubProduct;
}
