package com.bacpham.kanban_service.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GhnWebhookPayload {

    @JsonProperty("ShopID")
    private Integer shopId;

    @JsonProperty("Time")
    private String time;

    @JsonProperty("OrderCode")
    private String orderCode;

    @JsonProperty("ClientOrderCode")
    private String clientOrderCode;

    @JsonProperty("Type")
    private String type;

    @JsonProperty("Description")
    private String description;

    @JsonProperty("Status")
    private String status;

    @JsonProperty("Reason")
    private String reason;

    @JsonProperty("ReasonCode")
    private String reasonCode;

    @JsonProperty("CODAmount")
    private Double codAmount;

    @JsonProperty("CODTransferDate")
    private String codTransferDate;

    @JsonProperty("Weight")
    private Integer weight;

    @JsonProperty("ConvertedWeight")
    private Integer convertedWeight;

    @JsonProperty("Length")
    private Integer length;

    @JsonProperty("Width")
    private Integer width;

    @JsonProperty("Height")
    private Integer height;

    @JsonProperty("PaymentType")
    private Integer paymentType;

    @JsonProperty("IsPartialReturn")
    private Boolean isPartialReturn;

    @JsonProperty("PartialReturnCode")
    private String partialReturnCode;

    @JsonProperty("Fee")
    private Map<String, Object> fee;

    @JsonProperty("TotalFee")
    private Double totalFee;

    @JsonProperty("Warehouse")
    private String warehouse;

    @JsonProperty("ShipperName")
    private String shipperName;

    @JsonProperty("ShipperPhone")
    private String shipperPhone;

    @JsonProperty("PodURL")
    private String podUrl;
}
