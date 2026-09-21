package com.bacpham.kanban_service.service.impl;

import com.bacpham.kanban_service.dto.request.CalculateShippingFeeRequest;
import com.bacpham.kanban_service.dto.request.CreateShipmentRequest;
import com.bacpham.kanban_service.dto.response.ShipmentResponse;
import com.bacpham.kanban_service.entity.Order;
import com.bacpham.kanban_service.entity.OrderItem;
import com.bacpham.kanban_service.entity.Shipment;
import com.bacpham.kanban_service.entity.ShipmentItem;
import com.bacpham.kanban_service.enums.OrderStatus;
import com.bacpham.kanban_service.enums.PaymentType;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.repository.OrderItemRepository;
import com.bacpham.kanban_service.repository.OrderRepository;
import com.bacpham.kanban_service.repository.ShipmentItemRepository;
import com.bacpham.kanban_service.repository.ShipmentRepository;
import com.bacpham.kanban_service.service.IGhnShippingService;
import com.bacpham.kanban_service.service.IShipmentService;
import com.bacpham.kanban_service.utils.shipping.GhnStatusMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShipmentServiceImpl implements IShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final ShipmentItemRepository shipmentItemRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final IGhnShippingService ghnShippingService;
    private final GhnStatusMapper ghnStatusMapper;

    @Override
    @Transactional
    public ShipmentResponse createShipment(CreateShipmentRequest request) {
        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new AppException(ErrorCode.BILL_NOT_FOUND));

        // 1. Tạo mã kiện hàng nội bộ
        String shipmentCode = generateShipmentCode(order.getId());

        // 2. Tính toán tiền thu hộ COD
        Double codAmount = request.getCodAmount();
        if (codAmount == null) {
            codAmount = order.getPaymentType() == PaymentType.COD ? order.getTotal() : 0.0;
        }

        // 3. Khởi tạo đối tượng Shipment
        Shipment shipment = Shipment.builder()
                .order(order)
                .shipmentCode(shipmentCode)
                .carrier("GHN")
                .shippingStatus("ready_to_pick")
                .weight(request.getWeight())
                .length(request.getLength())
                .width(request.getWidth())
                .height(request.getHeight())
                .codAmount(codAmount)
                .shippingFee(0.0)
                .note(request.getNote())
                .requiredNote(request.getRequiredNote() != null ? request.getRequiredNote() : "CHOXEMHANGKHONGTHU")
                .items(new ArrayList<>())
                .build();

        // 4. Gắn các sản phẩm được đóng gói trong kiện hàng
        Map<String, OrderItem> orderItemMap = order.getItems().stream()
                .collect(Collectors.toMap(OrderItem::getId, item -> item));

        for (CreateShipmentRequest.ShipmentItemPackRequest packItem : request.getItems()) {
            OrderItem orderItem = orderItemMap.get(packItem.getOrderItemId());
            if (orderItem == null) {
                throw new AppException(ErrorCode.ORDER_ITEM_NOT_IN_ORDER);
            }
            if (packItem.getQuantity() > orderItem.getQuantity()) {
                throw new AppException(ErrorCode.INVALID_SHIPMENT_QUANTITY);
            }

            ShipmentItem shipmentItem = ShipmentItem.builder()
                    .shipment(shipment)
                    .orderItem(orderItem)
                    .quantity(packItem.getQuantity())
                    .build();

            shipment.getItems().add(shipmentItem);
        }

        // Lưu Shipment trước khi gửi sang GHN để có ID
        shipment = shipmentRepository.save(shipment);

        // 5. Bắn vận đơn sang GHN Open API
        try {
            String trackingCode = ghnShippingService.createShippingOrderFromShipment(shipment);
            if (trackingCode != null && !trackingCode.isBlank()) {
                shipment.setTrackingCode(trackingCode);
                shipment.setShippingStatus("ready_to_pick");

                // Cập nhật ngược lại cho Order để đảm bảo tương thích ngược
                order.setTrackingCode(trackingCode);
                order.setShippingStatus("ready_to_pick");
                if (order.getOrderStatus() == OrderStatus.PENDING) {
                    order.setOrderStatus(OrderStatus.PROCESSING);
                }
                orderRepository.save(order);
                shipment = shipmentRepository.save(shipment);
                log.info("Tạo Shipment {} thành công với trackingCode {}", shipmentCode, trackingCode);
            }
        } catch (Exception e) {
            log.error("Không thể bắn đơn sang GHN cho Shipment {}: {}", shipmentCode, e.getMessage());
            throw new RuntimeException("Tạo vận đơn GHN thất bại: " + e.getMessage(), e);
        }

        return mapToShipmentResponse(shipment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShipmentResponse> getShipmentsByOrderId(String orderId) {
        List<Shipment> shipments = shipmentRepository.findByOrderId(orderId);
        return shipments.stream()
                .map(this::mapToShipmentResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public com.bacpham.kanban_service.dto.response.PageResponse<ShipmentResponse> getShipmentsPage(
            int page, int pageSize, String status, String search) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                Math.max(0, page - 1), pageSize);

        String cleanStatus = (status != null && !status.isBlank() && !status.equalsIgnoreCase("ALL")) ? status.trim() : null;
        String cleanSearch = (search != null && !search.isBlank()) ? search.trim() : null;

        org.springframework.data.domain.Page<Shipment> shipmentPage = shipmentRepository.findShipmentsWithFilter(
                cleanStatus, cleanSearch, pageable);

        List<ShipmentResponse> items = shipmentPage.getContent().stream()
                .map(this::mapToShipmentResponse)
                .collect(Collectors.toList());

        return com.bacpham.kanban_service.dto.response.PageResponse.<ShipmentResponse>builder()
                .currentPage(page)
                .pageSize(pageSize)
                .totalPages(shipmentPage.getTotalPages())
                .totalElements(shipmentPage.getTotalElements())
                .data(items)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ShipmentResponse getShipmentById(String shipmentId) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new AppException(ErrorCode.SHIPMENT_NOT_FOUND));
        return mapToShipmentResponse(shipment);
    }

    @Override
    public Double calculateShippingFee(CalculateShippingFeeRequest request) {
        if (request.getOrderId() != null && !request.getOrderId().isBlank()) {
            Order order = orderRepository.findById(request.getOrderId()).orElse(null);
            if (order != null && order.getAddress() != null) {
                // Nếu chưa truyền districtId/wardCode thì có thể dùng từ địa chỉ đơn hàng
            }
        }
        return ghnShippingService.calculateShippingFee(request);
    }

    private String generateShipmentCode(String orderId) {
        String dateStr = new SimpleDateFormat("yyyyMMdd").format(new Date());
        String randomSuffix = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        return "SHIP-" + dateStr + "-" + randomSuffix;
    }

    private ShipmentResponse mapToShipmentResponse(Shipment shipment) {
        List<ShipmentResponse.ShipmentItemResponse> itemResponses = new ArrayList<>();
        if (shipment.getItems() != null) {
            itemResponses = shipment.getItems().stream().map(item -> {
                OrderItem oi = item.getOrderItem();
                String productTitle = "Sản phẩm";
                String variantName = "";
                Double price = 0.0;
                if (oi != null) {
                    price = oi.getPriceAtOrderTime();
                    if (oi.getSubProduct() != null) {
                        variantName = oi.getSubProduct().getSize() != null ? oi.getSubProduct().getSize() : "";
                        if (oi.getSubProduct().getProduct() != null) {
                            productTitle = oi.getSubProduct().getProduct().getTitle();
                        }
                    }
                }
                return ShipmentResponse.ShipmentItemResponse.builder()
                        .id(item.getId())
                        .orderItemId(oi != null ? oi.getId() : null)
                        .productTitle(productTitle)
                        .variantName(variantName)
                        .quantity(item.getQuantity())
                        .price(price)
                        .build();
            }).collect(Collectors.toList());
        }

        return ShipmentResponse.builder()
                .id(shipment.getId())
                .orderId(shipment.getOrder() != null ? shipment.getOrder().getId() : null)
                .shipmentCode(shipment.getShipmentCode())
                .carrier(shipment.getCarrier())
                .trackingCode(shipment.getTrackingCode())
                .shippingStatus(shipment.getShippingStatus())
                .shippingStatusName(ghnStatusMapper.toDisplayName(shipment.getShippingStatus()))
                .weight(shipment.getWeight())
                .length(shipment.getLength())
                .width(shipment.getWidth())
                .height(shipment.getHeight())
                .codAmount(shipment.getCodAmount())
                .shippingFee(shipment.getShippingFee())
                .note(shipment.getNote())
                .requiredNote(shipment.getRequiredNote())
                .pickedDate(shipment.getPickedDate())
                .deliveredDate(shipment.getDeliveredDate())
                .createdAt(shipment.getCreatedAt())
                .items(itemResponses)
                .build();
    }
}
