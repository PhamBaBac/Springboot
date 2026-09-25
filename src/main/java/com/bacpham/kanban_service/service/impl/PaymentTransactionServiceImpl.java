package com.bacpham.kanban_service.service.impl;

import com.bacpham.kanban_service.dto.response.PageResponse;
import com.bacpham.kanban_service.dto.response.PaymentTransactionResponse;
import com.bacpham.kanban_service.entity.Order;
import com.bacpham.kanban_service.entity.PaymentTransaction;
import com.bacpham.kanban_service.enums.PaymentType;
import com.bacpham.kanban_service.enums.TransactionStatus;
import com.bacpham.kanban_service.enums.TransactionType;
import com.bacpham.kanban_service.repository.PaymentTransactionRepository;
import com.bacpham.kanban_service.service.IPaymentTransactionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class PaymentTransactionServiceImpl implements IPaymentTransactionService {

    private final PaymentTransactionRepository transactionRepository;

    @Autowired
    public PaymentTransactionServiceImpl(PaymentTransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Override

    @Transactional(propagation = Propagation.REQUIRED)
    public PaymentTransactionResponse recordTransaction(
            Order order,
            String transactionCode,
            String gatewayTransactionNo,
            PaymentType paymentType,
            TransactionType transactionType,
            Double amount,
            TransactionStatus status,
            String rawResponse,
            String note
    ) {
        if (order == null) {
            throw new IllegalArgumentException("Đơn hàng không được để trống khi ghi nhận giao dịch dòng tiền");
        }

        String effectiveCode = transactionCode;
        if (effectiveCode == null || effectiveCode.isBlank()) {
            effectiveCode = "TXN-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }

        PaymentTransaction txn = PaymentTransaction.builder()
                .order(order)
                .transactionCode(effectiveCode)
                .gatewayTransactionNo(gatewayTransactionNo)
                .paymentType(paymentType != null ? paymentType : order.getPaymentType())
                .transactionType(transactionType)
                .amount(amount != null ? amount : order.getTotal())
                .currency("VND")
                .status(status != null ? status : TransactionStatus.SUCCESS)
                .rawResponse(rawResponse)
                .note(note)
                .build();

        PaymentTransaction saved = transactionRepository.save(txn);
        log.info("Recorded financial ledger transaction: code={}, orderId={}, type={}, amount={}, status={}",
                saved.getTransactionCode(), order.getId(), saved.getTransactionType(), saved.getAmount(), saved.getStatus());

        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentTransactionResponse> getTransactionsByOrderId(String orderId) {
        return transactionRepository.findByOrderIdOrderByCreatedAtDesc(orderId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PaymentTransactionResponse> getPagedTransactions(int page, int pageSize) {
        int pageNumber = Math.max(0, page - 1);
        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        Page<PaymentTransaction> txnPage = transactionRepository.findAllByOrderByCreatedAtDesc(pageable);

        List<PaymentTransactionResponse> content = txnPage.getContent().stream()
                .map(this::toResponse)
                .toList();

        return PageResponse.<PaymentTransactionResponse>builder()
                .currentPage(page)
                .pageSize(pageSize)
                .totalElements(txnPage.getTotalElements())
                .totalPages(txnPage.getTotalPages())
                .data(content)
                .build();
    }

    private PaymentTransactionResponse toResponse(PaymentTransaction txn) {
        return PaymentTransactionResponse.builder()
                .id(txn.getId())
                .orderId(txn.getOrder() != null ? txn.getOrder().getId() : null)
                .transactionCode(txn.getTransactionCode())
                .gatewayTransactionNo(txn.getGatewayTransactionNo())
                .paymentType(txn.getPaymentType())
                .transactionType(txn.getTransactionType())
                .amount(txn.getAmount())
                .currency(txn.getCurrency())
                .status(txn.getStatus())
                .rawResponse(txn.getRawResponse())
                .note(txn.getNote())
                .createdAt(txn.getCreatedAt())
                .build();
    }
}
