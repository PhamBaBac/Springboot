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

    @Override
    @Transactional
    public Order createOrderFromSelectedItems(String userId, String paymentType, OrderCreateRequest request) {
        List<OrderItemRequest> items = request.getItems();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        double total = 0.0;
        List<OrderItem> orderItems = new ArrayList<>();

        for (OrderItemRequest dto : items) {
            // Tìm SubProduct theo ID
            SubProduct subProduct = subProductRepository.findById(dto.getSubProductId())
                    .orElseThrow(() -> new AppException(ErrorCode.SUB_PRODUCT_NOT_FOUND));

            // Kiểm tra số lượng còn lại
            if (subProduct.getQty() < dto.getCount()) {
                throw new AppException(ErrorCode.INSUFFICIENT_STOCK);
            }

            // Trừ tồn kho
            subProduct.setQty(subProduct.getQty() - dto.getCount());

            // Tính tổng tiền cho sản phẩm
            double itemTotal = dto.getPrice() * dto.getCount();
            total += itemTotal;

            // Tạo BillItem
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

//        OrderStatus  orderStatus;
//        switch (paymentTypeEnum) {
//            case COD -> {
//                orderStatus = O.UNPAID;
//            }
//            case VNPAY, CREDIT_CARD -> {
//                paymentStatus = PaymentStatus.PAID;
//            }
//            default -> throw new AppException(ErrorCode.INVALID_CREDENTIALS);
//        }
        Address address = addressRepository.findById(request.getAddressId())
                .orElseThrow(() -> new AppException(ErrorCode.ADDRESS_NOT_FOUND));

        // Tạo Bill
        Order order = Order.builder()
                .user(user)
                .address(address)
                .total(total)
                .orderStatus(OrderStatus.PENDING)
                .paymentType(paymentTypeEnum)
                .items(new ArrayList<>()) // để tránh null khi set vào BillItem
                .build();

        // Gắn từng BillItem vào Bill
        for (OrderItem item : orderItems) {
            item.setOrder(order);
        }

        order.setItems(orderItems);

        // Lưu Bill + BillItems vào DB
        orderRepository.save(order);

        // Sau khi billRepository.save(bill);
        List<String> subProductIds = items.stream()
                .map(OrderItemRequest::getSubProductId)
                .toList();

// Xóa các sản phẩm trong giỏ hàng trùng với các subProductId đã thanh toán
        cartRepository.deleteByCreatedByAndSubProductIds(user, subProductIds);

        return order;
    }

    @Override
    public List<OrderResponse> getOrdersByUserId(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        List<Order> orders = orderRepository.findByUser(user);

        if (orders.isEmpty()) {
            throw new AppException(ErrorCode.BILL_NOT_FOUND);
        }

        List<OrderResponse> responses = new ArrayList<>();

        for (Order order : orders) {
            for (OrderItem item : order.getItems()) {
                OrderResponse response = orderMapper.toOrderResponse(item);
                responses.add(response);
            }
        }

        return responses;
    }
    @Override
    public PageResponse<OrderDetailResponse> getPagedAllOrders(int page, int pageSize) {
        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by("createdAt").descending());
        Page<Order> orderPage = orderRepository.findAll(pageable);

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

}
