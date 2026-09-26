package com.bacpham.kanban_service.entity;

import com.bacpham.kanban_service.helper.base.model.BaseModel;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "shipments", indexes = {
        @Index(name = "idx_shipments_order_id", columnList = "order_id"),
        @Index(name = "idx_shipments_tracking_code", columnList = "tracking_code"),
        @Index(name = "idx_shipments_shipment_code", columnList = "shipment_code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shipment extends BaseModel {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonBackReference
    private Order order;

    @Column(name = "shipment_code", unique = true, length = 50)
    private String shipmentCode;

    @Builder.Default
    @Column(name = "carrier", length = 50)
    private String carrier = "GHN";

    @Column(name = "tracking_code", length = 50)
    private String trackingCode;

    @Column(name = "shipping_status", length = 50)
    private String shippingStatus;

    @Column(name = "weight")
    private Integer weight;

    @Column(name = "length")
    private Integer length;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "cod_amount")
    private Double codAmount;

    @Column(name = "shipping_fee")
    private Double shippingFee;

    @Column(name = "note")
    private String note;

    @Builder.Default
    @Column(name = "required_note", length = 50)
    private String requiredNote = "CHOXEMHANGKHONGTHU";

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "picked_date")
    private Date pickedDate;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "delivered_date")
    private Date deliveredDate;

    @OneToMany(mappedBy = "shipment", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    @Builder.Default
    private List<ShipmentItem> items = new ArrayList<>();
}
