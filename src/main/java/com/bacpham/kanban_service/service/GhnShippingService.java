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
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

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
    private final RestTemplate restTemplate = new RestTemplate();

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

                    // Đồng bộ với trạng thái đã cập nhật trong Database (từ Webhook)
                    orderRepository.findByTrackingCode(orderCode.trim()).ifPresent(order -> {
                        if (order.getShippingStatus() != null && !order.getShippingStatus().isBlank()) {
                            String currentGhnStatus = order.getShippingStatus();
                            tracking.setStatus(currentGhnStatus);
                            tracking.setStatusName(mapGhnStatusName(currentGhnStatus));

                            if (tracking.getLogs() == null) {
                                tracking.setLogs(new ArrayList<>());
                            }
                            boolean hasStatusInLogs = tracking.getLogs().stream()
                                    .anyMatch(l -> currentGhnStatus.equalsIgnoreCase(l.getStatus()));
                            if (!hasStatusInLogs) {
                                tracking.getLogs().add(0, ShippingTrackingResponse.TrackingLogItem.builder()
                                        .status(currentGhnStatus)
                                        .statusName(mapGhnStatusName(currentGhnStatus))
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

        // Fallback: nếu GHN trả về lỗi hoặc không phản hồi, lấy thông tin từ DB để trả về
        return orderRepository.findByTrackingCode(orderCode.trim())
                .map(order -> {
                    String st = order.getShippingStatus() != null ? order.getShippingStatus() : "ready_to_pick";
                    List<ShippingTrackingResponse.TrackingLogItem> fallbackLogs = new ArrayList<>();
                    fallbackLogs.add(ShippingTrackingResponse.TrackingLogItem.builder()
                            .status(st)
                            .statusName(mapGhnStatusName(st))
                            .updatedDate(order.getCreatedAt() != null ? order.getCreatedAt().toString() : java.time.LocalDateTime.now().toString())
                            .build());

                    return ShippingTrackingResponse.builder()
                            .orderCode(order.getTrackingCode())
                            .status(st)
                            .statusName(mapGhnStatusName(st))
                            .toName(order.getAddress() != null ? order.getAddress().getName() : null)
                            .toPhone(order.getAddress() != null ? order.getAddress().getPhoneNumber() : null)
                            .toAddress(order.getAddress() != null ? order.getAddress().getAddress() : null)
                            .logs(fallbackLogs)
                            .build();
                })
                .orElse(null);
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

        Map<String, Object> body = new HashMap<>();

        // Hình thức thanh toán cước: 2 (Người nhận trả cước), 1 (Shop trả cước)
        body.put("payment_type_id", 2);
        body.put("note", "Đơn hàng " + (order.getId() != null ? order.getId() : ""));
        body.put("required_note", "CHOXEMHANGKHONGTHU");

        // Địa chỉ kho gửi hàng của Shop (đề phòng shop chưa cấu hình kho trên portal GHN)
        body.put("from_name", "Kenny");
        body.put("from_phone", "0989937030");
        body.put("from_address", "123 Cầu Giấy, Hà Nội");
        body.put("from_ward_code", "1A0101");
        body.put("from_district_id", 1482);

        // Địa chỉ người nhận (hỗ trợ mô hình 2 cấp tên của GHN: is_new_to_address = true)
        Address address = order.getAddress();
        if (address != null) {
            body.put("to_name", address.getName() != null && !address.getName().isBlank() ? address.getName() : "Khách hàng");
            body.put("to_phone", address.getPhoneNumber() != null && !address.getPhoneNumber().isBlank() ? address.getPhoneNumber() : "0989937030");
            body.put("to_address", address.getAddress() != null && !address.getAddress().isBlank() ? address.getAddress() : "Việt Nam");
            if (address.getWard() != null && !address.getWard().isBlank()) {
                body.put("to_ward_name", address.getWard().trim());
            }
            if (address.getDistrict() != null && !address.getDistrict().isBlank()) {
                body.put("to_district_name", address.getDistrict().trim());
            }
            if (address.getProvince() != null && !address.getProvince().isBlank()) {
                body.put("to_province_name", address.getProvince().trim());
            }
            body.put("is_new_to_address", true);
        } else {
            log.warn("Đơn hàng {} không có địa chỉ cụ thể, sử dụng thông tin mặc định", order.getId());
            body.put("to_name", "Khách hàng");
            body.put("to_phone", "0989937030");
            body.put("to_address", "Hà Nội, Việt Nam");
            body.put("is_new_to_address", true);
        }

        // Tiền thu hộ COD (Nếu đã thanh toán VNPay thì COD = 0)
        if (order.getPaymentType() == PaymentType.COD) {
            body.put("cod_amount", (int) Math.round(order.getTotal()));
        } else {
            body.put("cod_amount", 0);
        }

        // Kích thước & Trọng lượng
        body.put("weight", 500); // 500g
        body.put("length", 20);
        body.put("width", 15);
        body.put("height", 10);
        body.put("service_type_id", 2); // Chuyển phát tiêu chuẩn

        // Danh sách sản phẩm trong đơn hàng
        List<Map<String, Object>> items = new ArrayList<>();
        if (order.getItems() != null && !order.getItems().isEmpty()) {
            int totalWeight = 0;
            for (OrderItem item : order.getItems()) {
                Map<String, Object> itemMap = new HashMap<>();
                String itemName = "Sản phẩm";
                if (item.getSubProduct() != null && item.getSubProduct().getProduct() != null && item.getSubProduct().getProduct().getTitle() != null) {
                    itemName = item.getSubProduct().getProduct().getTitle();
                }
                itemMap.put("name", itemName);
                int qty = item.getQuantity() != null ? item.getQuantity() : 1;
                itemMap.put("quantity", qty);
                itemMap.put("price", item.getPriceAtOrderTime() != null ? item.getPriceAtOrderTime().intValue() : 0);
                itemMap.put("weight", 200);
                totalWeight += 200 * qty;
                items.add(itemMap);
            }
            if (totalWeight > 0) {
                body.put("weight", totalWeight);
            }
        } else {
            Map<String, Object> itemMap = new HashMap<>();
            itemMap.put("name", "Đơn hàng " + (order.getId() != null ? order.getId() : ""));
            itemMap.put("quantity", 1);
            itemMap.put("price", (int) Math.round(order.getTotal()));
            itemMap.put("weight", 500);
            items.add(itemMap);
        }
        body.put("items", items);

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
     * Xử lý webhook từ GHN gửi về khi có thay đổi trạng thái hoặc dữ liệu vận đơn
     * @param payload Dữ liệu sự kiện từ GHN
     */
    @Override
    public void handleWebhookEvent(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return;
        }

        log.info("Nhận GHN Webhook event: {}", payload);

        // GHN có thể gửi OrderCode (PascalCase) hoặc order_code (snake_case)
        String orderCode = null;
        if (payload.get("OrderCode") != null) {
            orderCode = payload.get("OrderCode").toString();
        } else if (payload.get("order_code") != null) {
            orderCode = payload.get("order_code").toString();
        }

        // Trạng thái GHN (Status hoặc status)
        String ghnStatus = null;
        if (payload.get("Status") != null) {
            ghnStatus = payload.get("Status").toString();
        } else if (payload.get("status") != null) {
            ghnStatus = payload.get("status").toString();
        }

        if (orderCode == null || orderCode.isBlank()) {
            log.warn("GHN Webhook không tìm thấy OrderCode trong payload");
            return;
        }

        final String trackingCode = orderCode.trim();
        final String finalGhnStatus = ghnStatus != null ? ghnStatus.trim() : null;

        orderRepository.findByTrackingCode(trackingCode).ifPresentOrElse(order -> {
            boolean updated = false;

            if (finalGhnStatus != null && !finalGhnStatus.equalsIgnoreCase(order.getShippingStatus())) {
                order.setShippingStatus(finalGhnStatus);
                updated = true;

                // Tự động đồng bộ sang trạng thái OrderStatus của hệ thống
                String lowerStatus = finalGhnStatus.toLowerCase();
                if (lowerStatus.equals("delivered")) {
                    order.setOrderStatus(OrderStatus.COMPLETED);
                    log.info("GHN Webhook: Đơn hàng {} đã giao thành công (COMPLETED)", order.getId());
                } else if (lowerStatus.equals("cancel")) {
                    order.setOrderStatus(OrderStatus.CANCELLED);
                    if (order.getCancelReason() == null || order.getCancelReason().isBlank()) {
                        order.setCancelReason("Hủy vận đơn từ GHN");
                    }
                    log.info("GHN Webhook: Đơn hàng {} đã bị hủy trên GHN", order.getId());
                } else if (order.getOrderStatus() == OrderStatus.PENDING) {
                    order.setOrderStatus(OrderStatus.PROCESSING);
                }
            }

            if (updated) {
                orderRepository.save(order);
                log.info("GHN Webhook: Đã cập nhật đơn hàng {} với trackingCode {} sang trạng thái GHN {}",
                        order.getId(), trackingCode, finalGhnStatus);
            }
        }, () -> log.warn("GHN Webhook: Không tìm thấy đơn hàng có mã vận đơn {}", trackingCode));
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
        String statusName = mapGhnStatusName(status);

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
                            .statusName(mapGhnStatusName(logStatus))
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

    public static String mapGhnStatusName(String status) {
        if (status == null) return "Không xác định";
        return switch (status.toLowerCase()) {
            case "ready_to_pick" -> "Mới tạo đơn - Chờ lấy hàng";
            case "picking" -> "Shipper đang đi lấy hàng";
            case "cancel" -> "Đơn hàng đã hủy";
            case "money_collect_picking" -> "Đang thu tiền người gửi";
            case "picked" -> "Đã lấy hàng thành công";
            case "storing" -> "Hàng đã nhập kho GHN";
            case "transporting" -> "Đang luân chuyển hàng giữa các kho";
            case "sorting" -> "Đang phân loại hàng hóa";
            case "delivering" -> "Shipper đang trên đường giao hàng";
            case "money_collect_delivering" -> "Shipper đang thu tiền khi giao";
            case "delivered" -> "Giao hàng thành công";
            case "delivery_fail" -> "Giao hàng không thành công";
            case "waiting_to_return" -> "Chờ xác nhận chuyển hoàn";
            case "return" -> "Đang chuyển hoàn về người gửi";
            case "return_transporting" -> "Đang luân chuyển hàng hoàn";
            case "return_sorting" -> "Đang phân loại hàng hoàn";
            case "returning" -> "Shipper đang trả lại hàng cho shop";
            case "return_fail" -> "Trả hàng không thành công";
            case "returned" -> "Đã hoàn trả hàng về shop";
            case "exception" -> "Đơn hàng gặp sự cố ngoại lệ";
            case "damage" -> "Hàng hóa bị hư hỏng";
            case "lost" -> "Hàng hóa bị thất lạc";
            default -> status;
        };
    }
}
