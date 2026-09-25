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
import com.bacpham.kanban_service.enums.Role;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.mapper.OrderMapper;
import com.bacpham.kanban_service.repository.*;
import com.bacpham.kanban_service.service.IOrderService;
import com.bacpham.kanban_service.service.IPromotionService;
import com.bacpham.kanban_service.strategy.discount.DiscountCalculationResult;
import com.bacpham.kanban_service.strategy.discount.DiscountCalculator;
import com.bacpham.kanban_service.strategy.order.OrderStateMachine;
import com.bacpham.kanban_service.repository.specification.OrderSpecification;
import org.springframework.data.jpa.domain.Specification;
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
    private final DiscountCalculator discountCalculator;
    private final OrderStateMachine orderStateMachine;

    @Override
    @Transactional
    public Order createOrderFromSelectedItems(String userId, String paymentType, OrderCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        validateAndApplyPromotion(user, request.getCode());

        List<OrderItemRequest> sortedItems = sortItemsForDeadlockPrevention(request.getItems());

        double total = 0.0;
        double discountTotal = 0.0;
        List<OrderItem> orderItems = new ArrayList<>(sortedItems.size());
        for (OrderItemRequest dto : sortedItems) {
            ProcessedItemResult result = processOrderItem(dto);
            orderItems.add(result.item());
            total += result.total();
            discountTotal += (result.item().getDiscountAmount() != null ? result.item().getDiscountAmount() : 0.0);
        }
        double subtotal = total + discountTotal;

        Order order = buildAndSaveOrder(user, request.getAddressId(), paymentType, total, subtotal, discountTotal, orderItems);
        cleanupCartItems(user, request.getItems());

        return order;
    }

    private void validateAndApplyPromotion(User user, String code) {
        if (code != null && !code.isBlank()) {
            promotionService.applyPromotionCode(user.getId(), code);
        }
    }

    private List<OrderItemRequest> sortItemsForDeadlockPrevention(List<OrderItemRequest> items) {
        if (items == null) return Collections.emptyList();
        return items.stream()
                .sorted(Comparator.comparing(OrderItemRequest::getSubProductId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private ProcessedItemResult processOrderItem(OrderItemRequest dto) {
        SubProduct subProduct = subProductRepository.findById(dto.getSubProductId())
                .orElseThrow(() -> new AppException(ErrorCode.SUB_PRODUCT_NOT_FOUND));

        if (Boolean.TRUE.equals(subProduct.getDeleted())
                || subProduct.getProduct() == null
                || Boolean.TRUE.equals(subProduct.getProduct().getDeleted())) {
            throw new AppException(ErrorCode.SUB_PRODUCT_NOT_FOUND);
        }

        // Cập nhật tồn kho an toàn bằng Atomic SQL Update (Chống Race Condition & Over-selling triệt để)
        int updatedRows = subProductRepository.directDeductStock(dto.getSubProductId(), dto.getCount());
        if (updatedRows == 0) {
            throw new AppException(ErrorCode.INSUFFICIENT_STOCK);
        }

        // Strategy Pattern: Tính giảm giá độc lập bằng DiscountCalculator
        DiscountCalculationResult discountResult = discountCalculator.calculate(
                dto.getPrice(), dto.getCount(), dto.getDiscountValue());

        String productTitle = (subProduct.getProduct() != null) ? subProduct.getProduct().getTitle() : null;
        String firstImage = (subProduct.getImages() != null && !subProduct.getImages().isEmpty())
                ? subProduct.getImages().get(0)
                : null;

        OrderItem orderItem = OrderItem.builder()
                .subProduct(subProduct)
                .quantity(dto.getCount())
                .priceAtOrderTime(discountResult.unitPriceAfterDiscount())
                // --- SNAPSHOT DATA (Bảo toàn dữ liệu lịch sử) ---
                .productTitle(productTitle)
                .skuCode(subProduct.getId())
                .size(subProduct.getSize())
                .color(subProduct.getColor())
                .image(firstImage)
                .originalPrice(dto.getPrice())
                .cost(subProduct.getCost())
                .discountAmount(discountResult.discountAmount())
                .totalPrice(discountResult.itemTotal())
                .attributesSnapshot(subProduct.getAttributes())
                .build();

        return new ProcessedItemResult(orderItem, discountResult.itemTotal());
    }

    private Order buildAndSaveOrder(User user, String addressId, String paymentTypeStr, double total, double subtotal, double discountAmount, List<OrderItem> items) {
        PaymentType paymentType;
        try {
            paymentType = PaymentType.valueOf(paymentTypeStr.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new AppException(ErrorCode.ADDRESS_NOT_FOUND));

        Order order = Order.builder()
                .user(user)
                .address(address)
                .total(total)
                .subtotal(subtotal)
                .discountAmount(discountAmount)
                .orderStatus(OrderStatus.PENDING)
                .paymentType(paymentType)
                .customerHidden(false)
                // --- SNAPSHOT SHIPPING ADDRESS ---
                .recipientName(address.getName())
                .recipientPhone(address.getPhoneNumber())
                .shippingAddress(address.getAddress())
                .shippingProvince(address.getProvince())
                .shippingDistrict(address.getDistrict())
                .shippingWard(address.getWard())
                .items(new ArrayList<>())
                .build();

        for (OrderItem item : items) {
            item.setOrder(order);
        }
        order.setItems(items);

        return orderRepository.save(order);
    }

    private void cleanupCartItems(User user, List<OrderItemRequest> items) {
        if (items == null) return;
        List<String> subProductIds = items.stream()
                .map(OrderItemRequest::getSubProductId)
                .toList();
        cartRepository.deleteByCreatedByAndSubProductIds(user, subProductIds);
    }

    private record ProcessedItemResult(OrderItem item, double total) {}

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
                .map(item -> item.getSubProduct() != null ? item.getSubProduct().getId() : item.getSkuCode())
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        // 1 query duy nhất lấy tất cả combo (subProductId, orderId) đã review
        Set<String> reviewedKeys = reviewRepository
                .findByCreatedByIdAndSubProductIdInAndOrderIdIn(userId, subProductIds, orderIds)
                .stream()
                .filter(r -> r.getSubProduct() != null && r.getOrder() != null)
                .map(r -> r.getSubProduct().getId() + "::" + r.getOrder().getId())
                .collect(java.util.stream.Collectors.toSet());

        List<OrderResponse> responses = new ArrayList<>();
        for (Order order : orders) {
            for (OrderItem item : order.getItems()) {
                OrderResponse response = orderMapper.toOrderResponse(item);
                String spId = item.getSubProduct() != null ? item.getSubProduct().getId() : item.getSkuCode();
                String key = (spId != null ? spId : "") + "::" + order.getId();
                response.setIsReviewed(reviewedKeys.contains(key));
                responses.add(response);
            }
        }

        return responses;
    }

    @Override
    public PageResponse<OrderDetailResponse> getPagedAllOrders(int page, int pageSize) {
        return getPagedOrders(null, null, null, null, page, pageSize);
    }

    @Override
    public PageResponse<OrderDetailResponse> getPagedOrders(
            String status,
            String search,
            String startDate,
            String endDate,
            int page,
            int pageSize
    ) {
        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by("createdAt").descending());
        Specification<Order> spec = OrderSpecification.filter(status, search, startDate, endDate);
        Page<Order> orderPage = orderRepository.findAll(spec, pageable);

        if (orderPage.isEmpty()) {
            return PageResponse.<OrderDetailResponse>builder()
                    .currentPage(page)
                    .pageSize(pageSize)
                    .totalPages(orderPage.getTotalPages())
                    .totalElements(orderPage.getTotalElements())
                    .data(Collections.emptyList())
                    .build();
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
            log.info("Đơn hàng {} đã ở trạng thái ĐÃ HỦY, trả về thành công.", orderId);
            return;
        }

        if (!orderStateMachine.canCustomerCancel(order)) {
            log.warn("Không thể hủy đơn hàng {}: trạng thái hiện tại là {}, trackingCode: {}",
                    orderId, order.getOrderStatus(), order.getTrackingCode());
            throw new AppException(ErrorCode.CANNOT_CANCEL_ORDER);
        }

        UpdateStatusOrder cancelRequest = UpdateStatusOrder.builder()
                .orderStatus(OrderStatus.CANCELLED)
                .cancelReason("Khách hàng tự hủy đơn")
                .build();

        orderStateMachine.transition(order, OrderStatus.CANCELLED, cancelRequest);

        orderRepository.save(order);
        log.info("Đơn hàng {} đã được hủy thành công bởi người dùng {}", orderId, userId);
    }

    @Override
    public OrderDetailResponse getOrderById(String userId, String orderId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.BILL_NOT_FOUND));

        if (!order.getUser().getId().equals(userId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        return orderMapper.toOrderDetailResponse(order);
    }

    @Override

    public void deleteOrder(String userId, String orderId) {
        User currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.BILL_NOT_FOUND));

        boolean isAdmin = currentUser.getRole() == Role.ADMIN;

        if (!isAdmin && !order.getUser().getId().equals(userId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (isAdmin) {
            // Admin xóa đơn hàng -> đánh dấu deleted = true để ẩn hoàn toàn khỏi danh sách quản lý
            order.setDeleted(true);
        } else {
            // Khách hàng tự ẩn đơn hàng phía mình, giữ lại đơn cho Admin và Thống kê
            order.setCustomerHidden(true);
            order.setDeleted(false);
        }
        orderRepository.save(order);
    }

    @Override
    @Transactional
    public void updateOrderStatus(String orderId, UpdateStatusOrder status) {
        log.info("Đang cập nhật trạng thái đơn hàng {}: {}", orderId, status);

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
            orderStateMachine.transition(order, newStatus, status);
        }

        orderRepository.save(order);
        log.info("Cập nhật đơn hàng {} thành công. Trạng thái: {}, Mã vận đơn: {}", orderId, order.getOrderStatus(), order.getTrackingCode());
    }

    @PostConstruct
    public void recoverPreviouslyDeletedOrders() {
        try {
            int restoredCount = orderRepository.recoverDeletedOrders();
            if (restoredCount > 0) {
                log.info("Đã phục hồi {} đơn hàng bị xóa trước đó sang customerHidden=true, deleted=false", restoredCount);
            }
        } catch (Exception e) {
            log.warn("Không thể phục hồi các đơn hàng đã bị xóa trước đó: {}", e.getMessage());
        }
    }
}
