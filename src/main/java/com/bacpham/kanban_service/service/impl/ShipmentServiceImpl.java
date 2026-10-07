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
import com.bacpham.kanban_service.dto.request.UpdateStatusOrder;
import com.bacpham.kanban_service.dto.request.UserNotificationCreateRequest;
import com.bacpham.kanban_service.enums.UserNotificationType;
import com.bacpham.kanban_service.service.IUserNotificationService;
import com.bacpham.kanban_service.strategy.order.OrderStateMachine;
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
    private final OrderStateMachine orderStateMachine;
    private final IUserNotificationService userNotificationService;

    @Override
    @Transactional
    public ShipmentResponse createShipment(CreateShipmentRequest request) {
        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new AppException(ErrorCode.BILL_NOT_FOUND));

        String shipmentCode = generateShipmentCode(order.getId());

        Double codAmount = request.getCodAmount();
        if (codAmount == null) {
            codAmount = order.getPaymentType() == PaymentType.COD ? order.getTotal() : 0.0;
        }

        String carrier = (request.getCarrier() != null && !request.getCarrier().isBlank())
                ? request.getCarrier().trim().toUpperCase()
                : "GHN";

        Double fee = request.getShippingFee() != null
                ? request.getShippingFee()
                : (order.getShippingFee() != null ? order.getShippingFee() : 0.0);

        Shipment shipment = Shipment.builder()
                .order(order)
                .shipmentCode(shipmentCode)
                .carrier(carrier)
                .shippingStatus("ready_to_pick")
                .weight(request.getWeight() != null ? request.getWeight() : 200)
                .length(request.getLength() != null ? request.getLength() : 20)
                .width(request.getWidth() != null ? request.getWidth() : 15)
                .height(request.getHeight() != null ? request.getHeight() : 10)
                .codAmount(codAmount)
                .shippingFee(fee)
                .note(request.getNote())
                .requiredNote(request.getRequiredNote() != null ? request.getRequiredNote() : "CHOXEMHANGKHONGTHU")
                .items(new ArrayList<>())
                .build();

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

        shipment = shipmentRepository.save(shipment);

        String trackingCode = null;

        if ("GHN".equalsIgnoreCase(carrier)) {
            try {
                trackingCode = ghnShippingService.createShippingOrderFromShipment(shipment);
            } catch (Exception e) {
                log.error("Không thể bắn đơn sang GHN cho Shipment {}: {}", shipmentCode, e.getMessage());
                throw new RuntimeException("Tạo vận đơn GHN thất bại: " + e.getMessage(), e);
            }
        } else if ("SHOP_DELIVERY".equalsIgnoreCase(carrier)) {
            trackingCode = (request.getTrackingCode() != null && !request.getTrackingCode().isBlank())
                    ? request.getTrackingCode().trim()
                    : ("SHOP-" + shipmentCode);
            log.info("Tạo vận đơn tự giao bởi cửa hàng: {}", trackingCode);
        } else {
            trackingCode = (request.getTrackingCode() != null && !request.getTrackingCode().isBlank())
                    ? request.getTrackingCode().trim()
                    : (carrier + "-" + shipmentCode);
            log.info("Tạo vận đơn cho đối tác {}: {}", carrier, trackingCode);
        }

        if (trackingCode != null && !trackingCode.isBlank()) {
            shipment.setTrackingCode(trackingCode);
            shipment.setShippingStatus("ready_to_pick");

            order.setTrackingCode(trackingCode);
            order.setCarrier(carrier);
            order.setShippingStatus("ready_to_pick");
            if (order.getOrderStatus() == OrderStatus.PENDING) {
                orderStateMachine.transition(order, OrderStatus.PROCESSING,
                        UpdateStatusOrder.builder().orderStatus(OrderStatus.PROCESSING).trackingCode(trackingCode).carrier(carrier).build());
            }
            orderRepository.save(order);
            shipment = shipmentRepository.save(shipment);
            log.info("Tạo Shipment {} thành công với carrier {} và trackingCode {}", shipmentCode, carrier, trackingCode);

            if (order.getUser() != null && order.getUser().getId() != null) {
                try {
                    String shortId = order.getId().length() > 8 ? order.getId().substring(0, 8).toUpperCase() : order.getId();
                    String carrierDisplayName = switch (carrier) {
                        case "SHOP_DELIVERY" -> "Cửa hàng tự giao";
                        case "VIETTEL_POST" -> "Viettel Post";
                        case "GHTK" -> "Giao Hàng Tiết Kiệm (GHTK)";
                        case "J_AND_T" -> "J&T Express";
                        case "VNPOST" -> "VNPost Bưu điện";
                        case "GHN" -> "Giao Hàng Nhanh (GHN)";
                        default -> carrier;
                    };
                    userNotificationService.createNotification(UserNotificationCreateRequest.builder()
                            .userId(order.getUser().getId())
                            .title("Đơn hàng #" + shortId + " đang được chuẩn bị")
                            .content(String.format("Đơn hàng #%s đã được tạo vận đơn (%s - Mã vận đơn: %s). Đang chuẩn bị giao hàng.",
                                    shortId, carrierDisplayName, trackingCode))
                            .type(UserNotificationType.ORDER_STATUS)
                            .targetUrl("/profile?tab=orders")
                            .referenceId(order.getId())
                            .build());
                } catch (Exception ex) {
                    log.warn("Không thể gửi thông báo cho user {}: {}", order.getUser().getId(), ex.getMessage());
                }
            }
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
            int page, int pageSize, String status, String carrier, String search) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                Math.max(0, page - 1), pageSize);

        String cleanStatus = (status != null && !status.isBlank() && !status.equalsIgnoreCase("ALL")) ? status.trim() : null;
        String cleanCarrier = (carrier != null && !carrier.isBlank() && !carrier.equalsIgnoreCase("ALL")) ? carrier.trim() : null;
        String cleanSearch = (search != null && !search.isBlank()) ? search.trim() : null;

        org.springframework.data.domain.Page<Shipment> shipmentPage = shipmentRepository.findShipmentsWithFilter(
                cleanStatus, cleanCarrier, cleanSearch, pageable);
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
            if (order != null) {
                if (order.getShippingFee() != null) {
                    return order.getShippingFee();
                }
                if (order.getTotal() >= 400000.0) {
                    return 0.0;
                }
                return 20000.0;
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
