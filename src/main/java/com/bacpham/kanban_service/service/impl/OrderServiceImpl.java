package com.bacpham.kanban_service.service.impl;

import com.bacpham.kanban_service.dto.request.OrderCreateRequest;
import com.bacpham.kanban_service.dto.request.OrderItemRequest;
import com.bacpham.kanban_service.dto.response.OrderDetailResponse;
import com.bacpham.kanban_service.dto.response.OrderResponse;
import com.bacpham.kanban_service.dto.response.PageResponse;
import com.bacpham.kanban_service.entity.*;
import com.bacpham.kanban_service.enums.OrderStatus;
import com.bacpham.kanban_service.enums.PaymentStatus;
import com.bacpham.kanban_service.enums.PaymentType;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.mapper.OrderMapper;
import com.bacpham.kanban_service.repository.*;
import com.bacpham.kanban_service.service.IOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

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

    @Override
    @Transactional
    public Order createOrderFromSelectedItems(String userId, String paymentType, OrderCreateRequest request) {
        List<OrderItemRequest> items = request.getItems();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        double total = 0.0;
        List<OrderItem> orderItems = new ArrayList<>();

        for (OrderItemRequest dto : items) {
            SubProduct subProduct = subProductRepository.findById(dto.getSubProductId())
                    .orElseThrow(() -> new AppException(ErrorCode.SUB_PRODUCT_NOT_FOUND));

            if (subProduct.getQty() < dto.getCount()) {
                throw new AppException(ErrorCode.INSUFFICIENT_STOCK);
            }

            subProduct.setQty(subProduct.getQty() - dto.getCount());

            double itemTotal = dto.getPrice() * dto.getCount();
            total += itemTotal;

            OrderItem orderItem = OrderItem.builder()
                    .subProduct(subProduct)
                    .quantity(dto.getCount())
                    .priceAtOrderTime(dto.getPrice())
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
                .items(new ArrayList<>())
                .build();

        for (OrderItem item : orderItems) {
            item.setOrder(order);
        }

        order.setItems(orderItems);

        orderRepository.save(order);

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

        List<Order> orders = orderRepository.findByUserAndDeletedFalse(user);

        if (orders.isEmpty()) {
            throw new AppException(ErrorCode.BILL_NOT_FOUND);
        }

        List<OrderResponse> responses = new ArrayList<>();

        for (Order order : orders) {
            for (OrderItem item : order.getItems()) {
                OrderResponse response = orderMapper.toOrderResponse(item);


                boolean hasReviewed = reviewRepository.existsByCreatedByIdAndSubProductIdAndOrderId(
                        userId,
                        item.getSubProduct().getId(),
                        order.getId()
                );


                response.setIsReviewed(hasReviewed);

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

        if (order.getOrderStatus() != OrderStatus.PENDING) {
            throw new AppException(ErrorCode.CANNOT_CANCEL_ORDER);
        }

        order.setOrderStatus(OrderStatus.CANCELLED);

        for (OrderItem item : order.getItems()) {
            SubProduct subProduct = item.getSubProduct();
            subProduct.setQty(subProduct.getQty() + item.getQuantity());
        }

        orderRepository.save(order);
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
        //update lai delete la true
        order.setDeleted(true);
        orderRepository.save(order);
    }
}
