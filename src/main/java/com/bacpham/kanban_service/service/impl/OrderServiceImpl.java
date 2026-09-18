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
import com.bacpham.kanban_service.service.IOrderService;
import com.bacpham.kanban_service.service.IPromotionService;
import com.bacpham.kanban_service.strategy.discount.DiscountCalculationResult;
import com.bacpham.kanban_service.strategy.discount.DiscountCalculator;
import com.bacpham.kanban_service.strategy.order.InventoryRestocker;
import com.bacpham.kanban_service.strategy.order.OrderStatusHandlerRegistry;
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
    private final OrderStatusHandlerRegistry statusHandlerRegistry;
    private final InventoryRestocker inventoryRestocker;

    @Override
    @Transactional
    public Order createOrderFromSelectedItems(String userId, String paymentType, OrderCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        validateAndApplyPromotion(user, request.getCode());

        List<OrderItemRequest> sortedItems = sortItemsForDeadlockPrevention(request.getItems());

        double total = 0.0;
        List<OrderItem> orderItems = new ArrayList<>(sortedItems.size());
        for (OrderItemRequest dto : sortedItems) {
            ProcessedItemResult result = processOrderItem(dto);
            orderItems.add(result.item());
            total += result.total();
        }

        Order order = buildAndSaveOrder(user, request.getAddressId(), paymentType, total, orderItems);
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

        // Cập nhật tồn kho an toàn dưới khóa bi quan
        int currentStock = subProduct.getStock() != null ? subProduct.getStock() : 0;
        int currentQty = subProduct.getQty() != null ? subProduct.getQty() : 0;
        subProduct.setStock(currentStock - dto.getCount());
        subProduct.setQty(Math.max(0, currentQty - dto.getCount()));
        subProductRepository.save(subProduct);

        // Strategy Pattern: Tính giảm giá độc lập bằng DiscountCalculator
        DiscountCalculationResult discountResult = discountCalculator.calculate(
                dto.getPrice(), dto.getCount(), dto.getDiscountValue());

        OrderItem orderItem = OrderItem.builder()
                .subProduct(subProduct)
                .quantity(dto.getCount())
                .priceAtOrderTime(discountResult.unitPriceAfterDiscount())
                .build();

        return new ProcessedItemResult(orderItem, discountResult.itemTotal());
    }

    private Order buildAndSaveOrder(User user, String addressId, String paymentTypeStr, double total, List<OrderItem> items) {
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
                .orderStatus(OrderStatus.PENDING)
                .paymentType(paymentType)
                .customerHidden(false)
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
            log.info("Đơn hàng {} đã ở trạng thái ĐÃ HỦY, trả về thành công.", orderId);
            return;
        }

        if (order.getOrderStatus() != OrderStatus.PENDING) {
            log.warn("Không thể hủy đơn hàng {}: trạng thái hiện tại là {}", orderId, order.getOrderStatus());
            throw new AppException(ErrorCode.CANNOT_CANCEL_ORDER);
        }

        order.setOrderStatus(OrderStatus.CANCELLED);
        order.setCancelReason("Khách hàng tự hủy đơn");

        inventoryRestocker.restockOrderItems(order);

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
            if (!isValidStatusTransition(oldStatus, newStatus)) {
                log.warn("Chuyển đổi trạng thái đơn hàng không hợp lệ từ {} sang {} cho đơn hàng: {}", oldStatus, newStatus, orderId);
                throw new AppException(ErrorCode.INVALID_ORDER_STATUS_TRANSITION);
            }

            // Strategy Pattern: Ủy quyền xử lý side-effects cho handler tương ứng (GHN, restock kho, reason)
            statusHandlerRegistry.executeTransition(order, newStatus, status);
            order.setOrderStatus(newStatus);
        }

        orderRepository.save(order);
        log.info("Cập nhật đơn hàng {} thành công. Trạng thái: {}, Mã vận đơn: {}", orderId, order.getOrderStatus(), order.getTrackingCode());
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
