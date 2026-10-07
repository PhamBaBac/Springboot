package com.bacpham.kanban_service.service;

import com.bacpham.kanban_service.configuration.redis.GenericRedisService;
import com.bacpham.kanban_service.configuration.socket.NotificationSocketPublisher;
import com.bacpham.kanban_service.dto.request.*;
import com.bacpham.kanban_service.dto.response.ShippingTrackingResponse;
import com.bacpham.kanban_service.entity.Order;
import com.bacpham.kanban_service.entity.Shipment;
import com.bacpham.kanban_service.enums.GhnWebhookType;
import com.bacpham.kanban_service.enums.NotificationPriority;
import com.bacpham.kanban_service.enums.NotificationType;
import com.bacpham.kanban_service.enums.OrderStatus;
import com.bacpham.kanban_service.enums.UserNotificationType;
import com.bacpham.kanban_service.repository.OrderRepository;
import com.bacpham.kanban_service.repository.ShipmentRepository;
import com.bacpham.kanban_service.strategy.order.OrderStateMachine;
import com.bacpham.kanban_service.utils.shipping.GhnOrderRequestBuilder;
import com.bacpham.kanban_service.utils.shipping.GhnStatusMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class GhnShippingService implements IGhnShippingService {

    @Value("${application.ghn.base-url:https://dev-online-gateway.ghn.vn/shiip/public-api}")
    private String ghnBaseUrl;

    @Value("${application.ghn.token:e0578753-b28e-11f1-a973-aee5264794df}")
    private String ghnToken;

    @Value("${application.ghn.shop-id:221254}")
    private String ghnShopId;

    private final OrderRepository orderRepository;
    private final ShipmentRepository shipmentRepository;
    private final RestTemplate restTemplate;
    private final GhnStatusMapper ghnStatusMapper;
    private final GhnOrderRequestBuilder ghnOrderRequestBuilder;
    private final GenericRedisService<String, String, String> redisService;
    private final ObjectMapper objectMapper;

    @Autowired
    @Lazy
    private OrderStateMachine orderStateMachine;

    @Autowired
    @Lazy
    private IUserNotificationService userNotificationService;

    @Autowired
    @Lazy
    private IAdminNotificationService adminNotificationService;

    @Autowired
    @Lazy
    private NotificationSocketPublisher notificationSocketPublisher;

    /**
     * Tra cứu chi tiết hành trình vận đơn từ Giao Hàng Nhanh (GHN)
     * @param orderCode Mã đơn GHN (ví dụ: L5G7S1)
     * @return ShippingTrackingResponse chứa trạng thái và toàn bộ timeline bưu kiện
     */
    @Override
    public ShippingTrackingResponse getTrackingDetail(String orderCode) {
        if (orderCode == null || orderCode.trim().isEmpty()) {
            return null;
        }

        String url = ghnBaseUrl + "/v2/shipping-order/detail";

        HttpHeaders headers = createGhnHeaders();
        Map<String, String> requestBody = Map.of("order_code", orderCode.trim());
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

        try {
            log.info("Gọi GHN API tra cứu vận đơn: url={}, order_code={}", url, orderCode);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Object codeObj = body.get("code");
                int code = codeObj instanceof Number ? ((Number) codeObj).intValue() : 200;

                if (code == 200 && body.get("data") instanceof Map) {
                    Map<String, Object> data = (Map<String, Object>) body.get("data");
                    ShippingTrackingResponse tracking = mapToTrackingResponse(data);

                    orderRepository.findByTrackingCode(orderCode.trim()).ifPresent(order -> {
                        if (order.getShippingStatus() != null && !order.getShippingStatus().isBlank()) {
                            String currentGhnStatus = order.getShippingStatus();
                            tracking.setStatus(currentGhnStatus);
                            tracking.setStatusName(ghnStatusMapper.toDisplayName(currentGhnStatus));

                            if (tracking.getLogs() == null) {
                                tracking.setLogs(new ArrayList<>());
                            }
                            boolean hasStatusInLogs = tracking.getLogs().stream()
                                    .anyMatch(l -> currentGhnStatus.equalsIgnoreCase(l.getStatus()));
                            if (!hasStatusInLogs) {
                                tracking.getLogs().add(0, ShippingTrackingResponse.TrackingLogItem.builder()
                                        .status(currentGhnStatus)
                                        .statusName(ghnStatusMapper.toDisplayName(currentGhnStatus))
                                        .updatedDate(java.time.LocalDateTime.now().toString())
                                        .build());
                            }
                        }
                    });

                    return tracking;
                } else {
                    log.warn("GHN response message: {}", body.get("message"));
                }
            }
        } catch (Exception e) {
            log.error("Lỗi khi gọi API tra cứu GHN cho mã {}: {}", orderCode, e.getMessage());
        }

        return orderRepository.findByTrackingCode(orderCode.trim())
                .map(order -> {
                    String st = order.getShippingStatus() != null ? order.getShippingStatus() : "ready_to_pick";
                    List<ShippingTrackingResponse.TrackingLogItem> fallbackLogs = new ArrayList<>();
                    fallbackLogs.add(ShippingTrackingResponse.TrackingLogItem.builder()
                            .status(st)
                            .statusName(ghnStatusMapper.toDisplayName(st))
                            .updatedDate(order.getCreatedAt() != null ? order.getCreatedAt().toString() : java.time.LocalDateTime.now().toString())
                            .build());

                    return ShippingTrackingResponse.builder()
                            .orderCode(order.getTrackingCode())
                            .status(st)
                            .statusName(ghnStatusMapper.toDisplayName(st))
                            .toName(order.getAddress() != null ? order.getAddress().getName() : null)
                            .toPhone(order.getAddress() != null ? order.getAddress().getPhoneNumber() : null)
                            .toAddress(order.getAddress() != null ? order.getAddress().getAddress() : null)
                            .logs(fallbackLogs)
                            .build();
                })
                .orElse(null);
    }

    /**
     * Tra cứu hành trình vận đơn theo orderId trong hệ thống.
     * Controller chỉ cần gọi method này, không cần inject OrderRepository trực tiếp.
     * @param orderId ID đơn hàng trong hệ thống
     * @return ShippingTrackingResponse hoặc null nếu không tìm thấy
     */
    @Override
    public ShippingTrackingResponse getTrackingByOrderId(String orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return null;
        }
        if (order.getTrackingCode() == null || order.getTrackingCode().trim().isEmpty()) {
            return null;
        }
        return getTrackingDetail(order.getTrackingCode());
    }

    /**
     * Tự động tạo đơn hàng trên GHN Open API và trích xuất mã vận đơn (order_code)
     * @param order Đơn hàng cần tạo vận đơn
     * @return Mã vận đơn từ GHN (ví dụ: "L5G7S1")
     */
    @Override
    public String createShippingOrder(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("Đơn hàng không hợp lệ");
        }

        String url = ghnBaseUrl + "/v2/shipping-order/create";
        HttpHeaders headers = createGhnHeaders();

        Map<String, Object> body = ghnOrderRequestBuilder.build(order);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            log.info("Gọi GHN API tạo vận đơn: url={}, orderId={}", url, order.getId());
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> resBody = response.getBody();
                Object codeObj = resBody.get("code");
                int code = codeObj instanceof Number ? ((Number) codeObj).intValue() : 200;

                if (code == 200 && resBody.get("data") instanceof Map) {
                    Map<String, Object> data = (Map<String, Object>) resBody.get("data");
                    String orderCode = data.get("order_code") != null ? data.get("order_code").toString() : null;
                    log.info("GHN tạo đơn thành công! OrderId: {}, Mã vận đơn GHN: {}", order.getId(), orderCode);
                    return orderCode;
                } else {
                    String msg = resBody.get("message") != null ? resBody.get("message").toString() : "Lỗi phản hồi từ GHN";
                    log.warn("GHN từ chối tạo đơn: {}", msg);
                    throw new RuntimeException("GHN: " + msg);
                }
            }
        } catch (RestClientResponseException e) {
            log.error("GHN create order error HTTP {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Lỗi kết nối GHN (" + e.getStatusCode() + "): " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Lỗi khi tạo vận đơn GHN cho đơn hàng {}: {}", order.getId(), e.getMessage());
            throw new RuntimeException("Lỗi khi tạo vận đơn GHN: " + e.getMessage(), e);
        }

        return null;
    }

    /**
     * Tạo vận đơn trên GHN từ đối tượng Shipment (kê khai cân nặng & kích thước thực tế)
     */
    @Override
    public String createShippingOrderFromShipment(Shipment shipment) {
        if (shipment == null || shipment.getOrder() == null) {
            throw new IllegalArgumentException("Kiện hàng hoặc đơn hàng không hợp lệ");
        }

        String url = ghnBaseUrl + "/v2/shipping-order/create";
        HttpHeaders headers = createGhnHeaders();

        Map<String, Object> body = ghnOrderRequestBuilder.buildFromShipment(shipment);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            log.info("Gọi GHN API tạo vận đơn từ Shipment: url={}, shipmentId={}, orderId={}",
                    url, shipment.getId(), shipment.getOrder().getId());
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> resBody = response.getBody();
                Object codeObj = resBody.get("code");
                int code = codeObj instanceof Number ? ((Number) codeObj).intValue() : 200;

                if (code == 200 && resBody.get("data") instanceof Map) {
                    Map<String, Object> data = (Map<String, Object>) resBody.get("data");
                    String orderCode = data.get("order_code") != null ? data.get("order_code").toString() : null;

                    if (data.get("total_fee") != null) {
                        shipment.setShippingFee(((Number) data.get("total_fee")).doubleValue());
                    }

                    log.info("GHN tạo vận đơn thành công từ Shipment! Mã vận đơn: {}", orderCode);
                    return orderCode;
                } else {
                    String msg = resBody.get("message") != null ? resBody.get("message").toString() : "Lỗi phản hồi từ GHN";
                    log.warn("GHN từ chối tạo vận đơn: {}", msg);
                    throw new RuntimeException("GHN: " + msg);
                }
            }
        } catch (RestClientResponseException e) {
            log.error("GHN create order error HTTP {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Lỗi kết nối GHN (" + e.getStatusCode() + "): " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Lỗi khi tạo vận đơn GHN từ Shipment {}: {}", shipment.getId(), e.getMessage());
            throw new RuntimeException("Lỗi khi tạo vận đơn GHN: " + e.getMessage(), e);
        }

        return null;
    }

    /**
     * Tính cước phí giao hàng dự kiến từ GHN dựa vào cân nặng & kích thước
     */
    @Override
    public Double calculateShippingFee(CalculateShippingFeeRequest request) {
        String url = ghnBaseUrl + "/v2/shipping-order/fee";
        HttpHeaders headers = createGhnHeaders();

        Map<String, Object> body = new HashMap<>();
        body.put("from_district_id", 1482);
        body.put("from_ward_code", "1A0101");
        body.put("service_type_id", 2);

        if (request.getToDistrictId() != null) {
            body.put("to_district_id", request.getToDistrictId());
        }
        if (request.getToWardCode() != null) {
            body.put("to_ward_code", request.getToWardCode());
        }

        body.put("weight", request.getWeight() != null ? request.getWeight() : 500);
        body.put("length", request.getLength() != null ? request.getLength() : 20);
        body.put("width", request.getWidth() != null ? request.getWidth() : 15);
        body.put("height", request.getHeight() != null ? request.getHeight() : 10);

        if (request.getInsuranceValue() != null && request.getInsuranceValue() > 0) {
            body.put("insurance_value", request.getInsuranceValue().intValue());
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            log.info("Gọi GHN tính phí vận chuyển: url={}, request={}", url, body);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> resBody = response.getBody();
                if (resBody.get("data") instanceof Map) {
                    Map<String, Object> data = (Map<String, Object>) resBody.get("data");
                    Object totalObj = data.get("total");
                    if (totalObj instanceof Number) {
                        return ((Number) totalObj).doubleValue();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Không tính được phí GHN online, fallback về phí mặc định 20000: {}", e.getMessage());
        }

        return 20000.0;
    }

    /**
     * Xử lý webhook từ GHN gửi về dạng Map thô (backward compatibility)
     */
    @Override
    @Async("taskExecutor")
    @Transactional
    public void handleWebhookEvent(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return;
        }
        try {
            GhnWebhookPayload dto = objectMapper.convertValue(payload, GhnWebhookPayload.class);
            handleWebhookEvent(dto);
        } catch (Exception e) {
            log.error("Lỗi khi chuyển đổi GHN webhook payload từ Map: {}", e.getMessage(), e);
        }
    }

    /**
     * Xử lý webhook từ GHN gửi về khi có thay đổi trạng thái hoặc dữ liệu vận đơn
     * @param payload Dữ liệu sự kiện từ GHN (chuẩn hóa DTO)
     */
    @Override
    @Async("taskExecutor")
    @Transactional
    public void handleWebhookEvent(GhnWebhookPayload payload) {
        if (payload == null) {
            return;
        }

        log.info("Nhận GHN Webhook event: type={}, orderCode={}, clientOrderCode={}, status={}, time={}",
                payload.getType(), payload.getOrderCode(), payload.getClientOrderCode(), payload.getStatus(), payload.getTime());

        // 1. Kiểm tra tính lũy đẳng (Deduplication) theo OrderCode + Type + Time
        if (payload.getOrderCode() != null && !payload.getOrderCode().isBlank()
                && payload.getType() != null && !payload.getType().isBlank()
                && payload.getTime() != null && !payload.getTime().isBlank()) {
            String dedupKey = String.format("ghn:webhook:dedup:%s:%s:%s",
                    payload.getOrderCode().trim(),
                    payload.getType().trim(),
                    payload.getTime().trim());
            if (redisService != null) {
                Boolean isNew = redisService.setIfAbsent(dedupKey, "PROCESSED", Duration.ofHours(48));
                if (Boolean.FALSE.equals(isNew)) {
                    log.info("GHN Webhook: Đã bỏ qua sự kiện trùng lặp (dedupKey={})", dedupKey);
                    return;
                }
            }
        }

        final String trackingCode = (payload.getOrderCode() != null && !payload.getOrderCode().isBlank())
                ? payload.getOrderCode().trim() : null;
        final String clientCode = (payload.getClientOrderCode() != null && !payload.getClientOrderCode().isBlank())
                ? payload.getClientOrderCode().trim() : null;
        final String ghnStatus = (payload.getStatus() != null && !payload.getStatus().isBlank())
                ? payload.getStatus().trim() : null;
        final GhnWebhookType eventType = GhnWebhookType.fromValue(payload.getType());

        // 2. Tìm kiếm Shipment tương ứng
        Optional<Shipment> optShipment = Optional.empty();
        if (trackingCode != null) {
            optShipment = shipmentRepository.findByTrackingCode(trackingCode);
        }
        if (optShipment.isEmpty() && clientCode != null) {
            optShipment = shipmentRepository.findByShipmentCode(clientCode);
        }
        if (optShipment.isEmpty() && clientCode != null) {
            List<Shipment> orderShipments = shipmentRepository.findByOrderId(clientCode);
            if (!orderShipments.isEmpty()) {
                optShipment = Optional.of(orderShipments.get(0));
            }
        }
        if (optShipment.isEmpty() && trackingCode != null) {
            Optional<Order> orderWithTracking = orderRepository.findByTrackingCode(trackingCode);
            if (orderWithTracking.isPresent()) {
                List<Shipment> orderShipments = shipmentRepository.findByOrderId(orderWithTracking.get().getId());
                if (!orderShipments.isEmpty()) {
                    optShipment = Optional.of(orderShipments.get(0));
                }
            }
        }

        // 3. Cập nhật bảng Shipment
        optShipment.ifPresent(shipment -> {
            boolean shipmentUpdated = false;

            if (trackingCode != null && (shipment.getTrackingCode() == null || shipment.getTrackingCode().isBlank())) {
                shipment.setTrackingCode(trackingCode);
                shipmentUpdated = true;
            }

            if (ghnStatus != null && !ghnStatus.equalsIgnoreCase(shipment.getShippingStatus())) {
                shipment.setShippingStatus(ghnStatus);
                String lower = ghnStatus.toLowerCase();
                if (lower.equals("delivered")) {
                    shipment.setDeliveredDate(new Date());
                } else if (lower.equals("picked") || lower.equals("delivering")) {
                    if (shipment.getPickedDate() == null) {
                        shipment.setPickedDate(new Date());
                    }
                }
                shipmentUpdated = true;
            }

            if (payload.getWeight() != null && payload.getWeight() > 0) {
                shipment.setWeight(payload.getWeight());
                shipmentUpdated = true;
            }
            if (payload.getLength() != null && payload.getLength() > 0) {
                shipment.setLength(payload.getLength());
                shipmentUpdated = true;
            }
            if (payload.getWidth() != null && payload.getWidth() > 0) {
                shipment.setWidth(payload.getWidth());
                shipmentUpdated = true;
            }
            if (payload.getHeight() != null && payload.getHeight() > 0) {
                shipment.setHeight(payload.getHeight());
                shipmentUpdated = true;
            }

            if (payload.getTotalFee() != null && payload.getTotalFee() > 0) {
                shipment.setShippingFee(payload.getTotalFee());
                shipmentUpdated = true;
            }

            if (payload.getCodAmount() != null) {
                shipment.setCodAmount(payload.getCodAmount());
                shipmentUpdated = true;
            }

            // Đồng bộ Shipper, POD URL, Lý do vào Note
            String existingNote = shipment.getNote() != null ? shipment.getNote() : "";
            StringBuilder noteBuilder = new StringBuilder(existingNote);

            if (payload.getShipperName() != null && !payload.getShipperName().isBlank()) {
                String shipperInfo = "Shipper: " + payload.getShipperName()
                        + (payload.getShipperPhone() != null && !payload.getShipperPhone().isBlank() ? " (" + payload.getShipperPhone() + ")" : "");
                if (!noteBuilder.toString().contains(payload.getShipperName())) {
                    if (!noteBuilder.isEmpty()) noteBuilder.append(" | ");
                    noteBuilder.append(shipperInfo);
                    shipmentUpdated = true;
                }
            }

            if (payload.getPodUrl() != null && !payload.getPodUrl().isBlank()) {
                if (!noteBuilder.toString().contains(payload.getPodUrl())) {
                    if (!noteBuilder.isEmpty()) noteBuilder.append(" | ");
                    noteBuilder.append("POD: ").append(payload.getPodUrl());
                    shipmentUpdated = true;
                }
            }

            if (payload.getReason() != null && !payload.getReason().isBlank()) {
                String reasonStr = payload.getReason()
                        + (payload.getReasonCode() != null && !payload.getReasonCode().isBlank() ? " [" + payload.getReasonCode() + "]" : "");
                if (!noteBuilder.toString().contains(payload.getReason())) {
                    if (!noteBuilder.isEmpty()) noteBuilder.append(" | ");
                    noteBuilder.append("Lý do: ").append(reasonStr);
                    shipmentUpdated = true;
                }
            }

            if (Boolean.TRUE.equals(payload.getIsPartialReturn()) && payload.getPartialReturnCode() != null && !payload.getPartialReturnCode().isBlank()) {
                if (!noteBuilder.toString().contains(payload.getPartialReturnCode())) {
                    if (!noteBuilder.isEmpty()) noteBuilder.append(" | ");
                    noteBuilder.append("Đơn giao 1 phần (Mã trả: ").append(payload.getPartialReturnCode()).append(")");
                    shipmentUpdated = true;
                }
            }

            if (eventType == GhnWebhookType.COD && payload.getCodTransferDate() != null) {
                String codInfo = "Tiền COD chuyển ngày: " + payload.getCodTransferDate();
                if (!noteBuilder.toString().contains(codInfo)) {
                    if (!noteBuilder.isEmpty()) noteBuilder.append(" | ");
                    noteBuilder.append(codInfo);
                    shipmentUpdated = true;
                }
            }

            if (shipmentUpdated) {
                shipment.setNote(noteBuilder.toString());
                shipmentRepository.save(shipment);
                log.info("GHN Webhook: Đã cập nhật Shipment {} (status={}, fee={}, weight={})",
                        shipment.getShipmentCode(), shipment.getShippingStatus(), shipment.getShippingFee(), shipment.getWeight());
            }
        });

        // 4. Tìm kiếm Order tương ứng
        Optional<Order> optOrder = Optional.empty();
        if (trackingCode != null) {
            optOrder = orderRepository.findByTrackingCode(trackingCode);
        }
        if (optOrder.isEmpty() && optShipment.isPresent() && optShipment.get().getOrder() != null) {
            optOrder = Optional.of(optShipment.get().getOrder());
        }
        if (optOrder.isEmpty() && clientCode != null) {
            optOrder = orderRepository.findById(clientCode);
        }

        // 5. Cập nhật Order & State Transitions
        optOrder.ifPresentOrElse(order -> {
            boolean updated = false;

            if (trackingCode != null && (order.getTrackingCode() == null || order.getTrackingCode().isBlank())) {
                order.setTrackingCode(trackingCode);
                updated = true;
            }

            if (ghnStatus != null && !ghnStatus.equalsIgnoreCase(order.getShippingStatus())) {
                order.setShippingStatus(ghnStatus);
                updated = true;

                String lowerStatus = ghnStatus.toLowerCase();
                try {
                    if (lowerStatus.equals("delivered")) {
                        if (order.getOrderStatus() == OrderStatus.PENDING) {
                            orderStateMachine.transition(order, OrderStatus.PROCESSING,
                                    UpdateStatusOrder.builder().orderStatus(OrderStatus.PROCESSING).build());
                        }
                        if (order.getOrderStatus() == OrderStatus.PROCESSING) {
                            orderStateMachine.transition(order, OrderStatus.COMPLETED,
                                    UpdateStatusOrder.builder().orderStatus(OrderStatus.COMPLETED).build());
                            log.info("GHN Webhook: Đơn hàng {} đã giao thành công (COMPLETED)", order.getId());
                        }
                    } else if (lowerStatus.equals("cancel")) {
                        String cancelReason = (payload.getReason() != null && !payload.getReason().isBlank())
                                ? payload.getReason()
                                : ((order.getCancelReason() != null && !order.getCancelReason().isBlank())
                                        ? order.getCancelReason()
                                        : "Hủy vận đơn từ GHN");
                        orderStateMachine.transition(order, OrderStatus.CANCELLED,
                                UpdateStatusOrder.builder().orderStatus(OrderStatus.CANCELLED).cancelReason(cancelReason).build());
                        log.info("GHN Webhook: Đơn hàng {} đã bị hủy trên GHN", order.getId());
                    } else if (order.getOrderStatus() == OrderStatus.PENDING) {
                        orderStateMachine.transition(order, OrderStatus.PROCESSING,
                                UpdateStatusOrder.builder().orderStatus(OrderStatus.PROCESSING).build());
                    }
                } catch (Exception e) {
                    log.warn("GHN Webhook: Không thể chuyển trạng thái đơn hàng {} qua State Machine: {}", order.getId(), e.getMessage());
                }
            }

            // Đồng bộ trạng thái GHN sang tất cả Shipment của đơn hàng
            if (ghnStatus != null) {
                try {
                    List<Shipment> relatedShipments = shipmentRepository.findByOrderId(order.getId());
                    for (Shipment s : relatedShipments) {
                        boolean sChanged = false;
                        if (trackingCode != null && (s.getTrackingCode() == null || s.getTrackingCode().isBlank())) {
                            s.setTrackingCode(trackingCode);
                            sChanged = true;
                        }
                        if (!ghnStatus.equalsIgnoreCase(s.getShippingStatus())) {
                            s.setShippingStatus(ghnStatus);
                            String lower = ghnStatus.toLowerCase();
                            if (lower.equals("delivered")) {
                                s.setDeliveredDate(new Date());
                            } else if (lower.equals("picked") || lower.equals("delivering")) {
                                if (s.getPickedDate() == null) s.setPickedDate(new Date());
                            }
                            sChanged = true;
                        }
                        if (payload != null && payload.getReason() != null && !payload.getReason().isBlank()) {
                            String curNote = s.getNote() != null ? s.getNote() : "";
                            if (!curNote.contains(payload.getReason())) {
                                s.setNote(curNote.isEmpty() ? "Lý do: " + payload.getReason() : curNote + " | Lý do: " + payload.getReason());
                                sChanged = true;
                            }
                        }
                        if (sChanged) {
                            shipmentRepository.save(s);
                            log.info("GHN Webhook: Đã đồng bộ Shipment {} của đơn hàng {} sang trạng thái GHN {}",
                                    s.getShipmentCode(), order.getId(), ghnStatus);
                        }
                    }
                } catch (Exception ex) {
                    log.warn("Lỗi khi đồng bộ Shipment cho order {}: {}", order.getId(), ex.getMessage());
                }
            }

            if (updated) {
                orderRepository.save(order);
                log.info("GHN Webhook: Đã cập nhật đơn hàng {} với trackingCode {} sang trạng thái GHN {}",
                        order.getId(), trackingCode, ghnStatus);

                sendShippingNotifications(order, trackingCode, ghnStatus, payload);
            }
        }, () -> log.warn("GHN Webhook: Không tìm thấy đơn hàng cho trackingCode={}, clientOrderCode={}", trackingCode, clientCode));
    }

    private void sendShippingNotifications(Order order, String trackingCode, String ghnStatus, GhnWebhookPayload payload) {
        if (order == null || order.getUser() == null || order.getUser().getId() == null) {
            return;
        }
        try {
            String shortId = order.getId().length() > 8 ? order.getId().substring(0, 8).toUpperCase() : order.getId();
            String friendlyStatusName = ghnStatusMapper != null ? ghnStatusMapper.toDisplayName(ghnStatus) : ghnStatus;
            String title;
            String content;
            String lowerStatus = ghnStatus != null ? ghnStatus.toLowerCase() : "";

            if (lowerStatus.equals("delivered")) {
                title = "Đơn hàng #" + shortId + " đã giao thành công";
                content = "Đơn hàng #" + shortId + " (Mã vận đơn: " + trackingCode + ") đã được giao thành công tới quý khách. Cảm ơn bạn đã mua sắm!";
            } else if (lowerStatus.equals("delivering")) {
                title = "Đơn hàng #" + shortId + " đang được giao";
                content = "Bưu tá GHN đang trên đường giao đơn hàng #" + shortId + " (Mã vận đơn: " + trackingCode + ") đến địa chỉ của bạn.";
            } else if (lowerStatus.equals("picked")) {
                title = "Đơn vị vận chuyển đã nhận đơn hàng #" + shortId;
                content = "Đơn vị vận chuyển GHN đã nhận kiện hàng #" + shortId + " (Mã vận đơn: " + trackingCode + ") và bắt đầu vận chuyển.";
            } else if (lowerStatus.equals("cancel")) {
                title = "Đơn hàng #" + shortId + " đã hủy vận chuyển";
                content = "Vận đơn GHN " + trackingCode + " cho đơn hàng #" + shortId + " đã bị hủy."
                        + (payload != null && payload.getReason() != null && !payload.getReason().isBlank() ? " Lý do: " + payload.getReason() : "");
            } else if (lowerStatus.equals("delivery_fail")) {
                title = "Giao hàng thất bại đơn #" + shortId;
                content = "Giao hàng không thành công cho đơn #" + shortId + " (Mã vận đơn: " + trackingCode + ")."
                        + (payload != null && payload.getReason() != null && !payload.getReason().isBlank() ? " Lý do: " + payload.getReason() : "");
            } else {
                title = "Cập nhật vận đơn #" + shortId;
                content = "Vận đơn GHN " + trackingCode + " đã chuyển sang trạng thái: " + friendlyStatusName;
            }

            if (userNotificationService != null) {
                userNotificationService.createNotification(UserNotificationCreateRequest.builder()
                        .userId(order.getUser().getId())
                        .title(title)
                        .content(content)
                        .type(UserNotificationType.ORDER_STATUS)
                        .targetUrl("/profile?tab=orders")
                        .referenceId(order.getId())
                        .build());
            }

            if (notificationSocketPublisher != null) {
                notificationSocketPublisher.sendOrderStatusUpdateToUser(
                        order.getUser().getId(),
                        order.getId(),
                        order.getOrderStatus().name(),
                        order.getCancelReason());
            }

            if (adminNotificationService != null && (lowerStatus.equals("delivery_fail") || lowerStatus.equals("damage") || lowerStatus.equals("lost") || lowerStatus.equals("cancel"))) {
                try {
                    NotificationType adminNotiType = lowerStatus.equals("cancel")
                            ? NotificationType.ORDER_CANCEL
                            : NotificationType.SYSTEM_ALERT;

                    adminNotificationService.createNotification(AdminNotificationRequest.builder()
                            .title("Cảnh báo vận chuyển: Đơn hàng #" + shortId + " gặp sự cố (" + friendlyStatusName + ")")
                            .content("Đơn hàng #" + shortId + " (Mã GHN: " + trackingCode + ") gặp sự cố: " + content)
                            .type(adminNotiType)
                            .priority(NotificationPriority.HIGH)
                            .targetUrl("/orders?id=" + order.getId())
                            .referenceId(order.getId())
                            .recipientRole("ADMIN")
                            .build());
                } catch (Exception ex) {
                    log.warn("Không thể gửi thông báo admin khi sự cố vận chuyển: {}", ex.getMessage());
                }
            }
        } catch (Exception ex) {
            log.warn("GHN Webhook: Không thể gửi thông báo cho user {}: {}", order.getUser().getId(), ex.getMessage());
        }
    }

    private HttpHeaders createGhnHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Token", ghnToken);
        if (ghnShopId != null && !ghnShopId.trim().isEmpty()) {
            headers.set("ShopId", ghnShopId.trim());
        }
        return headers;
    }

    private ShippingTrackingResponse mapToTrackingResponse(Map<String, Object> data) {
        String status = data.get("status") != null ? data.get("status").toString() : "";
        String statusName = ghnStatusMapper.toDisplayName(status);

        List<ShippingTrackingResponse.TrackingLogItem> logs = new ArrayList<>();
        if (data.get("log") instanceof List) {
            List<?> rawLogs = (List<?>) data.get("log");
            for (Object item : rawLogs) {
                if (item instanceof Map) {
                    Map<?, ?> logMap = (Map<?, ?>) item;
                    String logStatus = logMap.get("status") != null ? logMap.get("status").toString() : "";
                    String updatedDate = logMap.get("updated_date") != null ? logMap.get("updated_date").toString() : "";
                    logs.add(ShippingTrackingResponse.TrackingLogItem.builder()
                            .status(logStatus)
                            .statusName(ghnStatusMapper.toDisplayName(logStatus))
                            .updatedDate(updatedDate)
                            .build());
                }
            }
        }

        Double totalFee = null;
        if (data.get("total_fee") instanceof Number) {
            totalFee = ((Number) data.get("total_fee")).doubleValue();
        }

        return ShippingTrackingResponse.builder()
                .orderCode(data.get("order_code") != null ? data.get("order_code").toString() : null)
                .status(status)
                .statusName(statusName)
                .toName(data.get("to_name") != null ? data.get("to_name").toString() : null)
                .toPhone(data.get("to_phone") != null ? data.get("to_phone").toString() : null)
                .toAddress(data.get("to_address") != null ? data.get("to_address").toString() : null)
                .expectedDeliveryTime(data.get("leadtime") != null ? data.get("leadtime").toString() : null)
                .shippingFee(totalFee)
                .logs(logs)
                .rawData(data)
                .build();
    }
}
