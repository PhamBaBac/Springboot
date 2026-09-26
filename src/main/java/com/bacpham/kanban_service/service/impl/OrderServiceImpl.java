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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.bacpham.kanban_service.enums.NotificationPriority;
import com.bacpham.kanban_service.enums.NotificationType;
import com.bacpham.kanban_service.event.NotificationEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.bacpham.kanban_service.enums.TransactionStatus;
import com.bacpham.kanban_service.enums.TransactionType;
import com.bacpham.kanban_service.service.IOrderStatusHistoryService;
import com.bacpham.kanban_service.service.IPaymentTransactionService;

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
    private final IOrderStatusHistoryService orderStatusHistoryService;
    private final IPaymentTransactionService paymentTransactionService;
    private final ApplicationEventPublisher eventPublisher;

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

        Order order = buildAndSaveOrder(user, request.getAddressId(), paymentType, total, subtotal, discountTotal,
                orderItems);
        cleanupCartItems(user, request.getItems());

        orderStatusHistoryService.logStatusChange(order, null, OrderStatus.PENDING, "Tạo mới đơn hàng thành công",
                "Hình thức thanh toán: " + paymentType);

        if (order.getPaymentType() == PaymentType.COD) {
            paymentTransactionService.recordTransaction(
                    order,
                    "COD-" + order.getId(),
                    null,
                    PaymentType.COD,
                    TransactionType.PAYMENT,
                    order.getTotal(),
                    TransactionStatus.PENDING,
                    null,
                    "Đơn hàng COD - Chờ thanh toán khi giao hàng");
        }

        String shortOrderId = order.getId().length() > 8 ? order.getId().substring(0, 8).toUpperCase() : order.getId();
        String customerName = user.getFirstname() != null ? (user.getFirstname() + (user.getLastname() != null ? " " + user.getLastname() : "")) : "Khách hàng";
        eventPublisher.publishEvent(NotificationEvent.of(
                this,
                NotificationType.ORDER_NEW,
                "Đơn hàng mới #" + shortOrderId,
                String.format("%s vừa đặt đơn hàng #%s trị giá %,.0f đ", customerName, shortOrderId, order.getTotal()),
                NotificationPriority.HIGH,
                "/orders?id=" + order.getId() + "&status=PENDING",
                order.getId()
        ));

        return order;
    }

    private void validateAndApplyPromotion(User user, String code) {
        if (code != null && !code.isBlank()) {
            promotionService.applyPromotionCode(user.getId(), code);
        }
    }

    private List<OrderItemRequest> sortItemsForDeadlockPrevention(List<OrderItemRequest> items) {
        if (items == null)
            return Collections.emptyList();
        return items.stream()
                .sorted(Comparator.comparing(OrderItemRequest::getSubProductId,
                        Comparator.nullsLast(Comparator.naturalOrder())))
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

        int updatedRows = subProductRepository.directDeductStock(dto.getSubProductId(), dto.getCount());
        if (updatedRows == 0) {
            throw new AppException(ErrorCode.INSUFFICIENT_STOCK);
        }

        int currentStock = subProduct.getStock() != null ? subProduct.getStock() : 0;
        int remainingStock = currentStock - dto.getCount();
        String prodTitle = (subProduct.getProduct() != null) ? subProduct.getProduct().getTitle() : "Sản phẩm";
        String variantInfo = String.format("%s (%s / %s)", prodTitle,
                subProduct.getColor() != null ? subProduct.getColor() : "-",
                subProduct.getSize() != null ? subProduct.getSize() : "-");

        if (remainingStock <= 0) {
            eventPublisher.publishEvent(NotificationEvent.of(
                    this,
                    NotificationType.OUT_OF_STOCK,
                    "Sản phẩm đã hết hàng!",
                    "Biến thể " + variantInfo + " đã hết hàng trong kho.",
                    NotificationPriority.URGENT,
                    "/inventory",
                    subProduct.getId()
            ));
        } else if (remainingStock <= 5) {
            eventPublisher.publishEvent(NotificationEvent.of(
                    this,
                    NotificationType.LOW_STOCK,
                    "Cảnh báo sắp hết hàng",
                    "Biến thể " + variantInfo + " chỉ còn lại " + remainingStock + " sản phẩm trong kho.",
                    NotificationPriority.HIGH,
                    "/inventory",
                    subProduct.getId()
            ));
        }

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
                .productTitle(productTitle)
                .skuCode((subProduct.getSku() != null && !subProduct.getSku().isBlank()) ? subProduct.getSku()
                        : subProduct.getId())
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

    private Order buildAndSaveOrder(User user, String addressId, String paymentTypeStr, double total, double subtotal,
            double discountAmount, List<OrderItem> items) {
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
        if (items == null)
            return;
        List<String> subProductIds = items.stream()
                .map(OrderItemRequest::getSubProductId)
                .toList();
        cartRepository.deleteByCreatedByAndSubProductIds(user, subProductIds);
    }

    private record ProcessedItemResult(OrderItem item, double total) {
    }

    @Override
    public List<OrderResponse> getOrdersByUserId(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        List<Order> orders = orderRepository.findByUserAndNotCustomerHidden(user);

        if (orders.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> orderIds = orders.stream().map(Order::getId).toList();
        List<String> subProductIds = orders.stream()
                .flatMap(o -> o.getItems().stream())
                .map(item -> item.getSubProduct() != null ? item.getSubProduct().getId() : item.getSkuCode())
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

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
            int pageSize) {
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

        List<String> orderIds = orderPage.getContent().stream()
                .map(Order::getId)
                .toList();

        Map<String, Order> ordersWithItems = orderRepository.findAllWithItemsByIds(orderIds)
                .stream()
                .collect(java.util.stream.Collectors.toMap(Order::getId, o -> o));

        List<OrderDetailResponse> responses = orderPage.getContent().stream()
                .map(o -> orderMapper.toOrderDetailResponse(
                        ordersWithItems.getOrDefault(o.getId(), o)))
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

        String shortOrderId = order.getId().length() > 8 ? order.getId().substring(0, 8).toUpperCase() : order.getId();
        eventPublisher.publishEvent(NotificationEvent.of(
                this,
                NotificationType.ORDER_CANCEL,
                "Đơn hàng đã bị hủy #" + shortOrderId,
                String.format("Đơn hàng #%s đã bị khách hàng hủy. Lý do: %s",
                        shortOrderId,
                        order.getCancelReason() != null ? order.getCancelReason() : "Khách hàng tự hủy đơn"),
                NotificationPriority.URGENT,
                "/orders?id=" + order.getId() + "&status=CANCELLED",
                order.getId()
        ));
    }

    @Override
    public OrderDetailResponse getOrderById(String userId, String orderId) {
        User currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.BILL_NOT_FOUND));

        boolean isStaff = currentUser.getRole() == Role.ADMIN || currentUser.getRole() == Role.MANAGER;

        if (!isStaff && !order.getUser().getId().equals(userId)) {
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
            order.setDeleted(true);
        } else {
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
        log.info("Cập nhật đơn hàng {} thành công. Trạng thái: {}, Mã vận đơn: {}", orderId, order.getOrderStatus(),
                order.getTrackingCode());
    }

    @Override
    public Map<String, Long> getOrderStatusCounts() {
        List<Object[]> rows = orderRepository.countOrdersByStatus();
        Map<String, Long> counts = new HashMap<>();
        for (OrderStatus status : OrderStatus.values()) {
            counts.put(status.name(), 0L);
        }
        long allCount = 0;
        for (Object[] row : rows) {
            OrderStatus status = (OrderStatus) row[0];
            Long count = ((Number) row[1]).longValue();
            if (status != null) {
                counts.put(status.name(), count);
                allCount += count;
            }
        }
        counts.put("ALL", allCount);
        return counts;
    }

    @PostConstruct
    public void recoverPreviouslyDeletedOrders() {
        try {
            int restoredCount = orderRepository.recoverDeletedOrders();
            if (restoredCount > 0) {
                log.info("Đã phục hồi {} đơn hàng bị xóa trước đó sang customerHidden=true, deleted=false",
                        restoredCount);
            }
        } catch (Exception e) {
            log.warn("Không thể phục hồi các đơn hàng đã bị xóa trước đó: {}", e.getMessage());
        }
    }
}
