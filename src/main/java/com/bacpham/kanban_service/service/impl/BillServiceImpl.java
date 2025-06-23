package com.bacpham.kanban_service.service.impl;

import com.bacpham.kanban_service.dto.response.BillResponse;
import com.bacpham.kanban_service.entity.*;
import com.bacpham.kanban_service.enums.BillStatus;
import com.bacpham.kanban_service.enums.PaymentStatus;
import com.bacpham.kanban_service.enums.PaymentType;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.mapper.BillMapper;
import com.bacpham.kanban_service.repository.BillRepository;
import com.bacpham.kanban_service.repository.CartRepository;
import com.bacpham.kanban_service.repository.UserRepository;
import com.bacpham.kanban_service.service.IBillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class BillServiceImpl implements IBillService {
    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final BillRepository billRepository;
    private final BillMapper billMapper;

    @Override
    public Bill createBillFromCart(String userId, String paymentType) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        List<Cart> carts = cartRepository.findByCreatedBy(user);

        if (carts.isEmpty()) {
            throw new AppException(ErrorCode.CART_NOT_FOUND);
        }

        double total = 0.0;
        List<BillItem> billItems = new ArrayList<>();

        for (Cart cart : carts) {
            double itemTotal = cart.getCount() * cart.getPrice();
            total += itemTotal;

            SubProduct subProduct = cart.getSubProduct();
            int newQty = subProduct.getQty() - cart.getCount();

            subProduct.setQty(newQty);

            BillItem billItem = BillItem.builder()
                    .subProduct(subProduct)
                    .quantity(cart.getCount())
                    .priceAtOrderTime(cart.getPrice())
                    .build();

            billItems.add(billItem);
        }

        Bill bill = Bill.builder()
                .user(user)
                .total(total)
                .status(BillStatus.COMPLETED)
                .paymentStatus(PaymentStatus.PAID)
                .paymentType(PaymentType.valueOf(paymentType.toUpperCase()))
                .items(new ArrayList<>())
                .build();

        for (BillItem item : billItems) {
            item.setBill(bill);
        }

        bill.setItems(billItems);

        billRepository.save(bill);
        cartRepository.deleteAll(carts);

        return bill;
    }


    @Override
    public List<BillResponse> getBillsByUserId(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        List<Bill> bills = billRepository.findByUser(user);

        if (bills.isEmpty()) {
            throw new AppException(ErrorCode.BILL_NOT_FOUND);
        }

        List<BillResponse> responses = new ArrayList<>();

        for (Bill bill : bills) {
            for (BillItem item : bill.getItems()) {
                BillResponse response = billMapper.toBillResponse(item);
                response.setStatus(bill.getStatus().toString());
                responses.add(response);
            }
        }

        return responses;
    }


}
