package com.bacpham.kanban_service.service.impl;

import com.bacpham.kanban_service.dto.request.DiscountRequest;
import com.bacpham.kanban_service.dto.request.OrderCreateRequest;
import com.bacpham.kanban_service.dto.request.OrderItemRequest;
import com.bacpham.kanban_service.dto.request.UpdateStatusOrder;
import com.bacpham.kanban_service.dto.response.OrderDetailResponse;
import com.bacpham.kanban_service.dto.response.OrderResponse;
import com.bacpham.kanban_service.dto.response.PageResponse;
import com.bacpham.kanban_service.entity.*;
import com.bacpham.kanban_service.enums.OrderStatus;
import com.bacpham.kanban_service.enums.PaymentType;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.mapper.OrderMapper;
import com.bacpham.kanban_service.repository.*;
import com.bacpham.kanban_service.service.IGhnShippingService;
import com.bacpham.kanban_service.service.IOrderService;
import com.bacpham.kanban_service.service.IPromotionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.annotation.PostConstruct;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class OrderServiceImpl implements IOrderService {
    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final SubProductRepository subProductRepository;
    private final AddressRepository addressRepository;
    private final ReviewProductRepository reviewRepository;
    private final IPromotionService promotionService;
    private final IGhnShippingService ghnShippingService;

    @Override
    @Transactional
    public Order createOrderFromSelectedItems(String userId, String paymentType, OrderCreateRequest request) {
        List<OrderItemRequest> items = request.getItems();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        double total = 0.0;
        List<OrderItem> orderItems = new ArrayList<>();

        if (request.getCode() != null && !request.getCode().isBlank()) {
            promotionService.applyPromotionCode(user.getId(), request.getCode());
        }

        // A-1 Concurrency Fix:
        // Sắp xếp items theo subProductId để đảm bảo tất cả các transaction khóa theo cùng một thứ tự.
        // Điều này ngăn chặn triệt để tình trạng Deadlock giữa 2 đơn hàng chứa cùng các sản phẩm.
        List<OrderItemRequest> sortedItems = items.stream()
                .sorted(Comparator.comparing(OrderItemRequest::getSubProductId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        for (OrderItemRequest dto : sortedItems) {
            // Khóa bi quan (Pessimistic Write Lock: SELECT ... FOR UPDATE)
            // Ngăn chặn 2 transaction cùng đọc 1 số lượng tồn kho rồi ghi đè (Lost Update / Overselling)
            SubProduct subProduct = subProductRepository.findByIdWithLock(dto.getSubProductId())
                    .orElseThrow(() -> new AppException(ErrorCode.SUB_PRODUCT_NOT_FOUND));

            if (Boolean.TRUE.equals(subProduct.getDeleted())
                    || subProduct.getProduct() == null
                    || Boolean.TRUE.equals(subProduct.getProduct().getDeleted())) {
                throw new AppException(ErrorCode.SUB_PRODUCT_NOT_FOUND);
            }

            if (subProduct.getStock() < dto.getCount()) {
                throw new AppException(ErrorCode.INSUFFICIENT_STOCK);
            }

            // Cập nhật tồn kho an toàn dưới khóa
            int currentStock = subProduct.getStock() != null ? subProduct.getStock() : 0;
            int currentQty = subProduct.getQty() != null ? subProduct.getQty() : 0;
            subProduct.setStock(currentStock - dto.getCount());
            subProduct.setQty(Math.max(0, currentQty - dto.getCount()));
            subProductRepository.save(subProduct);

            double itemTotal = dto.getPrice() * dto.getCount();
            double unitPriceAfterDiscount = dto.getPrice();

            DiscountRequest discount = dto.getDiscountValue();
            if (discount != null && discount.getValue() != null && discount.getType() != null) {
                try {
                    double discountValue = Double.parseDouble(discount.getValue());

                    switch (discount.getType()) {
                        case DISCOUNT -> {
                            double discountAmount = discountValue;
                            itemTotal -= discountAmount;
                            unitPriceAfterDiscount = itemTotal / dto.getCount();
                        }
                        case PERCENT -> {
                            double percent = discountValue / 100.0;
                            itemTotal *= (1 - percent);
                            unitPriceAfterDiscount = itemTotal / dto.getCount();
                        }
                        default -> throw new AppException(ErrorCode.INVALID_PROMOTION_TYPE);
                    }
                } catch (NumberFormatException e) {
                    throw new AppException(ErrorCode.INVALID_PROMOTION_VALUE);
                }
            }

            // Đảm bảo không âm
            if (itemTotal < 0) {
                itemTotal = 0;
                unitPriceAfterDiscount = 0;
            }

            total += itemTotal;

            OrderItem orderItem = OrderItem.builder()
                    .subProduct(subProduct)
                    .quantity(dto.getCount())
                    .priceAtOrderTime(unitPriceAfterDiscount) // Lưu giá đã giảm cho mỗi đơn vị
                    .build();

            orderItems.add(orderItem);
        }

        PaymentType paymentTypeEnum;
        try {
            paymentTypeEnum = PaymentType.valueOf(paymentType.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        Address address = addressRepository.findById(request.getAddressId())
                .orElseThrow(() -> new AppException(ErrorCode.ADDRESS_NOT_FOUND));

        Order order = Order.builder()
                .user(user)
                .address(address)
                .total(total)
                .orderStatus(OrderStatus.PENDING)
                .paymentType(paymentTypeEnum)
                .customerHidden(false)
                .items(new ArrayList<>())
                .build();

        for (OrderItem item : orderItems) {
            item.setOrder(order);
        }

        order.setItems(orderItems);

        orderRepository.save(order);

        // Xóa các sản phẩm trong giỏ hàng đã đặt mua
        List<String> subProductIds = items.stream()
                .map(OrderItemRequest::getSubProductId)
                .toList();

        cartRepository.deleteByCreatedByAndSubProductIds(user, subProductIds);

        return order;
    }

    @Override
    public List<OrderResponse> getOrdersByUserId(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        List<Order> orders = orderRepository.findByUserAndNotCustomerHidden(user);

        if (orders.isEmpty()) {
            return Collections.emptyList();
        }

        // Fix N+1: Gom tất cả orderId và subProductId, load reviewed status 1 lần duy nhất
        // Thay vì N×M queries `existsByReviewed` trong vòng lặp
        List<String> orderIds = orders.stream().map(Order::getId).toList();
        List<String> subProductIds = orders.stream()
                .flatMap(o -> o.getItems().stream())
                .map(item -> item.getSubProduct().getId())
                .distinct()
                .toList();

        // 1 query duy nhất lấy tất cả combo (subProductId, orderId) đã review
        Set<String> reviewedKeys = reviewRepository
                .findByCreatedByIdAndSubProductIdInAndOrderIdIn(userId, subProductIds, orderIds)
                .stream()
                .map(r -> r.getSubProduct().getId() + "::" + r.getOrder().getId())
                .collect(java.util.stream.Collectors.toSet());

        List<OrderResponse> responses = new ArrayList<>();
        for (Order order : orders) {
            for (OrderItem item : order.getItems()) {
                OrderResponse response = orderMapper.toOrderResponse(item);
                String key = item.getSubProduct().getId() + "::" + order.getId();
                response.setIsReviewed(reviewedKeys.contains(key));
                responses.add(response);
            }
        }

        return responses;
    }

    @Override
    public PageResponse<OrderDetailResponse> getPagedAllOrders(int page, int pageSize) {
        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by("createdAt").descending());
        Page<Order> orderPage = orderRepository.findAllByDeletedFalse(pageable);

        if (orderPage.isEmpty()) {
            throw new AppException(ErrorCode.BILL_NOT_FOUND);
        }

        List<OrderDetailResponse> responses = orderPage.getContent().stream()
                .map(orderMapper::toOrderDetailResponse)
                .toList();

        return PageResponse.<OrderDetailResponse>builder()
                .currentPage(page)
                .pageSize(orderPage.getSize())
                .totalPages(orderPage.getTotalPages())
                .totalElements(orderPage.getTotalElements())
                .data(responses)
                .build();
    }

    @Override
    @Transactional
    public void cancelOrder(String userId, String orderId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.BILL_NOT_FOUND));

        if (!order.getUser().getId().equals(userId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        // Nếu đơn hàng đã ở trạng thái CANCELLED (idempotent), trả về thành công ngay để tránh lỗi khi click đúp
        if (order.getOrderStatus() == OrderStatus.CANCELLED) {
            log.info("Order {} is already CANCELLED, returning success.", orderId);
            return;
        }

        if (order.getOrderStatus() != OrderStatus.PENDING) {
            log.warn("Cannot cancel order {}: current status in DB is {}", orderId, order.getOrderStatus());
            throw new AppException(ErrorCode.CANNOT_CANCEL_ORDER);
        }

        order.setOrderStatus(OrderStatus.CANCELLED);
        order.setCancelReason("Khách hàng tự hủy đơn");

        restockOrderItems(order);

        orderRepository.save(order);
        log.info("Order {} cancelled successfully by user {}", orderId, userId);
    }

    @Override
    public OrderDetailResponse getOrderById(String userId, String orderId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.BILL_NOT_FOUND));

        if (!order.getUser().getId().equals(userId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        return orderMapper.toOrderDetailResponse(order);
    }

    @Override

    public void deleteOrder(String userId, String orderId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.BILL_NOT_FOUND));

        if (!order.getUser().getId().equals(userId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        // Ẩn đơn hàng phía khách hàng, giữ lại đơn cho Admin và Thống kê
        order.setCustomerHidden(true);
        order.setDeleted(false);
        orderRepository.save(order);
    }

    @Override
    @Transactional
    public void updateOrderStatus(String orderId, UpdateStatusOrder status) {
        log.info("Updating order status for orderId: {} to status: {}", orderId, status);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.BILL_NOT_FOUND));

        if (status == null) {
            throw new AppException(ErrorCode.INVALID_KEY);
        }

        OrderStatus oldStatus = order.getOrderStatus();
        OrderStatus newStatus = status.getOrderStatus() != null ? status.getOrderStatus() : oldStatus;

        if (status.getTrackingCode() != null && !status.getTrackingCode().isBlank()) {
            order.setTrackingCode(status.getTrackingCode().trim());
        }

        if (newStatus != oldStatus) {
            if (!isValidStatusTransition(oldStatus, newStatus)) {
                log.warn("Invalid order status transition from {} to {} for orderId: {}", oldStatus, newStatus, orderId);
                throw new AppException(ErrorCode.INVALID_ORDER_STATUS_TRANSITION);
            }

            // Tự động gọi API GHN tạo đơn khi chuyển sang PROCESSING (nếu chưa có mã vận đơn)
            if (newStatus == OrderStatus.PROCESSING) {
                if (order.getTrackingCode() == null || order.getTrackingCode().isBlank()) {
                    try {
                        String ghnCode = ghnShippingService.createShippingOrder(order);
                        if (ghnCode != null && !ghnCode.isBlank()) {
                            order.setTrackingCode(ghnCode);
                            order.setShippingStatus("ready_to_pick");
                            log.info("Tự động tạo đơn GHN thành công cho orderId {}: trackingCode={}", orderId, ghnCode);
                        }
                    } catch (Exception e) {
                        log.error("Không thể tự động tạo đơn qua GHN cho orderId {}: {}", orderId, e.getMessage());
                        throw new RuntimeException("Tự động tạo đơn GHN thất bại: " + e.getMessage(), e);
                    }
                }
            }

            if (newStatus == OrderStatus.CANCELLED) {
                String reason = status.getCancelReason();
                if (reason == null || reason.trim().isEmpty()) {
                    reason = "Hủy bởi Quản trị viên";
                }
                order.setCancelReason(reason);
                restockOrderItems(order);
            } else if (newStatus == OrderStatus.REFUNDED) {
                if (status.getCancelReason() != null && !status.getCancelReason().trim().isEmpty()) {
                    order.setCancelReason(status.getCancelReason());
                }
                restockOrderItems(order);
            }

            order.setOrderStatus(newStatus);
        }

        orderRepository.save(order);
        log.info("Order {} updated successfully. Status: {}, TrackingCode: {}", orderId, order.getOrderStatus(), order.getTrackingCode());
    }

    private boolean isValidStatusTransition(OrderStatus from, OrderStatus to) {
        if (from == to) {
            return true;
        }
        return switch (from) {
            case PENDING -> to == OrderStatus.PROCESSING || to == OrderStatus.CANCELLED;
            case PROCESSING -> to == OrderStatus.COMPLETED || to == OrderStatus.CANCELLED;
            case COMPLETED -> to == OrderStatus.REFUNDED;
            case CANCELLED, REFUNDED -> false;
        };
    }

    private void restockOrderItems(Order order) {
        if (order.getItems() == null) return;
        List<OrderItem> sortedItems = order.getItems().stream()
                .filter(item -> item.getSubProduct() != null && item.getSubProduct().getId() != null)
                .sorted(Comparator.comparing(item -> item.getSubProduct().getId()))
                .toList();

        for (OrderItem item : sortedItems) {
            SubProduct subProduct = subProductRepository.findByIdWithLock(item.getSubProduct().getId())
                    .orElse(item.getSubProduct());
            int currentStock = subProduct.getStock() != null ? subProduct.getStock() : 0;
            int currentQty = subProduct.getQty() != null ? subProduct.getQty() : 0;
            subProduct.setStock(currentStock + item.getQuantity());
            subProduct.setQty(currentQty + item.getQuantity());
            subProductRepository.save(subProduct);
        }
    }

    @PostConstruct
    public void recoverPreviouslyDeletedOrders() {
        try {
            int restoredCount = orderRepository.recoverDeletedOrders();
            if (restoredCount > 0) {
                log.info("Restored {} orders that were previously marked deleted to customerHidden=true, deleted=false", restoredCount);
            }
        } catch (Exception e) {
            log.warn("Could not recover previously deleted orders: {}", e.getMessage());
        }
    }
}
