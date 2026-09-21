package com.bacpham.kanban_service.service;

import com.bacpham.kanban_service.dto.response.ShippingTrackingResponse;
import com.bacpham.kanban_service.entity.Address;
import com.bacpham.kanban_service.entity.Order;
import com.bacpham.kanban_service.entity.OrderItem;
import com.bacpham.kanban_service.enums.OrderStatus;
import com.bacpham.kanban_service.enums.PaymentType;
import com.bacpham.kanban_service.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import com.bacpham.kanban_service.utils.shipping.GhnStatusMapper;
import com.bacpham.kanban_service.utils.shipping.GhnOrderRequestBuilder;
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
    private final com.bacpham.kanban_service.repository.ShipmentRepository shipmentRepository;
    private final RestTemplate restTemplate; // Inject qua Spring DI (bean trong ApplicationConfig)
    private final GhnStatusMapper ghnStatusMapper; // Inject mapper de tuan thu SRP va OCP
    private final GhnOrderRequestBuilder ghnOrderRequestBuilder; // Inject builder de tuan thu SRP

    /**
     * Tra cá»©u chi tiáº¿t hÃ nh trÃ¬nh váº­n Ä‘Æ¡n tá»« Giao HÃ ng Nhanh (GHN)
     * @param orderCode MÃ£ Ä‘Æ¡n GHN (vÃ­ dá»¥: L5G7S1)
     * @return ShippingTrackingResponse chá»©a tráº¡ng thÃ¡i vÃ  toÃ n bá»™ timeline bÆ°u kiá»‡n
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
            log.info("Gá»i GHN API tra cá»©u váº­n Ä‘Æ¡n: url={}, order_code={}", url, orderCode);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Object codeObj = body.get("code");
                int code = codeObj instanceof Number ? ((Number) codeObj).intValue() : 200;

                if (code == 200 && body.get("data") instanceof Map) {
                    Map<String, Object> data = (Map<String, Object>) body.get("data");
                    ShippingTrackingResponse tracking = mapToTrackingResponse(data);

                    // Äá»“ng bá»™ vá»›i tráº¡ng thÃ¡i Ä‘Ã£ cáº­p nháº­t trong Database (tá»« Webhook)
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
            log.error("Lá»—i khi gá»i API tra cá»©u GHN cho mÃ£ {}: {}", orderCode, e.getMessage());
        }

        // Fallback: náº¿u GHN tráº£ vá» lá»—i hoáº·c khÃ´ng pháº£n há»“i, láº¥y thÃ´ng tin tá»« DB Ä‘á»ƒ tráº£ vá»
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
     * Tra cá»©u hÃ nh trÃ¬nh váº­n Ä‘Æ¡n theo orderId trong há»‡ thá»‘ng.
     * Controller chá»‰ cáº§n gá»i method nÃ y, khÃ´ng cáº§n inject OrderRepository trá»±c tiáº¿p.
     * @param orderId ID Ä‘Æ¡n hÃ ng trong há»‡ thá»‘ng
     * @return ShippingTrackingResponse hoáº·c null náº¿u khÃ´ng tÃ¬m tháº¥y
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
     * Tá»± Ä‘á»™ng táº¡o Ä‘Æ¡n hÃ ng trÃªn GHN Open API vÃ  trÃ­ch xuáº¥t mÃ£ váº­n Ä‘Æ¡n (order_code)
     * @param order Ä Æ¡n hÃ ng cáº§n táº¡o váº­n Ä‘Æ¡n
     * @return MÃ£ váº­n Ä‘Æ¡n tá»« GHN (vÃ­ dá»¥: "L5G7S1")
     */
    @Override
    public String createShippingOrder(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("Ä Æ¡n hÃ ng khÃ´ng há»£p lá»‡");
        }

        String url = ghnBaseUrl + "/v2/shipping-order/create";
        HttpHeaders headers = createGhnHeaders();

        Map<String, Object> body = ghnOrderRequestBuilder.build(order);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            log.info("Gá»i GHN API táº¡o váº­n Ä‘Æ¡n: url={}, orderId={}", url, order.getId());
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> resBody = response.getBody();
                Object codeObj = resBody.get("code");
                int code = codeObj instanceof Number ? ((Number) codeObj).intValue() : 200;

                if (code == 200 && resBody.get("data") instanceof Map) {
                    Map<String, Object> data = (Map<String, Object>) resBody.get("data");
                    String orderCode = data.get("order_code") != null ? data.get("order_code").toString() : null;
                    log.info("GHN táº¡o Ä‘Æ¡n thÃ nh cÃ´ng! OrderId: {}, MÃ£ váº­n Ä‘Æ¡n GHN: {}", order.getId(), orderCode);
                    return orderCode;
                } else {
                    String msg = resBody.get("message") != null ? resBody.get("message").toString() : "Lá»—i pháº£n há»“i tá»« GHN";
                    log.warn("GHN tá»« chá»‘i táº¡o Ä‘Æ¡n: {}", msg);
                    throw new RuntimeException("GHN: " + msg);
                }
            }
        } catch (RestClientResponseException e) {
            log.error("GHN create order error HTTP {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Lá»—i káº¿t ná»‘i GHN (" + e.getStatusCode() + "): " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Lá»—i khi táº¡o váº­n Ä‘Æ¡n GHN cho Ä‘Æ¡n hÃ ng {}: {}", order.getId(), e.getMessage());
            throw new RuntimeException("Lá»—i khi táº¡o váº­n Ä‘Æ¡n GHN: " + e.getMessage(), e);
        }

        return null;
    }

    /**
     * Tạo vận đơn trên GHN từ đối tượng Shipment (kê khai cân nặng & kích thước thực tế)
     */
    @Override
    public String createShippingOrderFromShipment(com.bacpham.kanban_service.entity.Shipment shipment) {
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

                    // Cập nhật phí ship trả về từ GHN nếu có
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
    public Double calculateShippingFee(com.bacpham.kanban_service.dto.request.CalculateShippingFeeRequest request) {
        String url = ghnBaseUrl + "/v2/shipping-order/fee";
        HttpHeaders headers = createGhnHeaders();

        Map<String, Object> body = new HashMap<>();
        body.put("from_district_id", 1482); // Kho Cầu Giấy Hà Nội
        body.put("from_ward_code", "1A0101");
        body.put("service_type_id", 2); // Chuẩn

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
            log.warn("Không tính được phí GHN online, fallback về phí mặc định 30000: {}", e.getMessage());
        }

        return 30000.0;
    }

    /**
     * Xá»­ lÃ½ webhook tá»« GHN gá»­i vá»  khi cÃ³ thay Ä‘á»•i tráº¡ng thÃ¡i hoáº·c dá»¯ liá»‡u váº­n Ä‘Æ¡n
     * @param payload Dá»¯ liá»‡u sá»± kiá»‡n tá»« GHN
     */
    @Override
    @Async("taskExecutor")
    @Transactional
    public void handleWebhookEvent(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return;
        }

        log.info("Nháº­n GHN Webhook event: {}", payload);

        // GHN cÃ³ thá»ƒ gá»­i OrderCode (PascalCase) hoáº·c order_code (snake_case)
        String orderCode = null;
        if (payload.get("OrderCode") != null) {
            orderCode = payload.get("OrderCode").toString();
        } else if (payload.get("order_code") != null) {
            orderCode = payload.get("order_code").toString();
        }

        // Tráº¡ng thÃ¡i GHN (Status hoáº·c status)
        String ghnStatus = null;
        if (payload.get("Status") != null) {
            ghnStatus = payload.get("Status").toString();
        } else if (payload.get("status") != null) {
            ghnStatus = payload.get("status").toString();
        }

        if (orderCode == null || orderCode.isBlank()) {
            log.warn("GHN Webhook khÃ´ng tÃ¬m tháº¥y OrderCode trong payload");
            return;
        }

        final String trackingCode = orderCode.trim();
        final String finalGhnStatus = ghnStatus != null ? ghnStatus.trim() : null;

        // Cập nhật thực thể Shipment nếu tồn tại
        shipmentRepository.findByTrackingCode(trackingCode).ifPresent(shipment -> {
            if (finalGhnStatus != null && !finalGhnStatus.equalsIgnoreCase(shipment.getShippingStatus())) {
                shipment.setShippingStatus(finalGhnStatus);
                String lower = finalGhnStatus.toLowerCase();
                if (lower.equals("delivered")) {
                    shipment.setDeliveredDate(new Date());
                } else if (lower.equals("picked") || lower.equals("delivering")) {
                    if (shipment.getPickedDate() == null) {
                        shipment.setPickedDate(new Date());
                    }
                }
                shipmentRepository.save(shipment);
                log.info("GHN Webhook: Đã cập nhật Shipment {} sang trạng thái {}", shipment.getShipmentCode(), finalGhnStatus);
            }
        });

        orderRepository.findByTrackingCode(trackingCode).ifPresentOrElse(order -> {
            boolean updated = false;

            if (finalGhnStatus != null && !finalGhnStatus.equalsIgnoreCase(order.getShippingStatus())) {
                order.setShippingStatus(finalGhnStatus);
                updated = true;

                // Tá»± Ä‘á»™ng Ä‘á»“ng bá»™ sang tráº¡ng thÃ¡i OrderStatus cá»§a há»‡ thá»‘ng
                String lowerStatus = finalGhnStatus.toLowerCase();
                if (lowerStatus.equals("delivered")) {
                    order.setOrderStatus(OrderStatus.COMPLETED);
                    log.info("GHN Webhook: Ä Æ¡n hÃ ng {} Ä‘Ã£ giao thÃ nh cÃ´ng (COMPLETED)", order.getId());
                } else if (lowerStatus.equals("cancel")) {
                    order.setOrderStatus(OrderStatus.CANCELLED);
                    if (order.getCancelReason() == null || order.getCancelReason().isBlank()) {
                        order.setCancelReason("Há»§y váº­n Ä‘Æ¡n tá»« GHN");
                    }
                    log.info("GHN Webhook: Ä Æ¡n hÃ ng {} Ä‘Ã£ bá»‹ há»§y trÃªn GHN", order.getId());
                } else if (order.getOrderStatus() == OrderStatus.PENDING) {
                    order.setOrderStatus(OrderStatus.PROCESSING);
                }
            }

            if (updated) {
                orderRepository.save(order);
                log.info("GHN Webhook: ÄÃ£ cáº­p nháº­t Ä‘Æ¡n hÃ ng {} vá»›i trackingCode {} sang tráº¡ng thÃ¡i GHN {}",
                        order.getId(), trackingCode, finalGhnStatus);
            }
        }, () -> log.warn("GHN Webhook: KhÃ´ng tÃ¬m tháº¥y Ä‘Æ¡n hÃ ng cÃ³ mÃ£ váº­n Ä‘Æ¡n {}", trackingCode));
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


