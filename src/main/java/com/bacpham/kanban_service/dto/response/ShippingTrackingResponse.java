package com.bacpham.kanban_service.dto.response;

import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShippingTrackingResponse {
    private String orderCode;
    private String status;
    private String statusName;
    private String toName;
    private String toPhone;
    private String toAddress;
    private String expectedDeliveryTime;
    private Double shippingFee;
    private List<TrackingLogItem> logs;
    private Map<String, Object> rawData;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TrackingLogItem {
        private String status;
        private String statusName;
        private String updatedDate;
    }
}
