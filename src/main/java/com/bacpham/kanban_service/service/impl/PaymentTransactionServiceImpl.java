package com.bacpham.kanban_service.service.impl;

import com.bacpham.kanban_service.dto.response.PageResponse;
import com.bacpham.kanban_service.dto.response.PaymentTransactionResponse;
import com.bacpham.kanban_service.entity.Order;
import com.bacpham.kanban_service.entity.PaymentTransaction;
import com.bacpham.kanban_service.enums.OrderStatus;
import com.bacpham.kanban_service.enums.PaymentType;
import com.bacpham.kanban_service.enums.TransactionStatus;
import com.bacpham.kanban_service.enums.TransactionType;
import com.bacpham.kanban_service.repository.PaymentTransactionRepository;
import com.bacpham.kanban_service.service.IPaymentTransactionService;
import jakarta.annotation.PostConstruct;
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

    @PostConstruct
    public void init() {
        // Đồng bộ dữ liệu lịch sử khi hệ thống khởi động
        syncPendingTransactions();
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
    @Transactional
    public List<PaymentTransactionResponse> getTransactionsByOrderId(String orderId) {
        List<PaymentTransaction> txns = transactionRepository.findByOrderIdOrderByCreatedAtDesc(orderId);

        // Tự động kiểm tra và đồng bộ trạng thái nếu đơn hàng đã COMPLETED hoặc CANCELLED
        for (PaymentTransaction txn : txns) {
            if (txn.getStatus() == TransactionStatus.PENDING && txn.getTransactionType() == TransactionType.PAYMENT) {
                Order order = txn.getOrder();
                if (order != null && order.getOrderStatus() == OrderStatus.COMPLETED) {
                    txn.setStatus(TransactionStatus.SUCCESS);
                    String updatedNote = (txn.getNote() != null && !txn.getNote().isBlank())
                            ? txn.getNote() + " -> Đã thu tiền khi hoàn thành đơn hàng"
                            : "Đã thu tiền khi giao hàng thành công (COD)";
                    txn.setNote(updatedNote);
                    transactionRepository.save(txn);
                    log.info("Tự động đồng bộ bút toán {} của đơn {} sang SUCCESS", txn.getTransactionCode(), order.getId());
                } else if (order != null && order.getOrderStatus() == OrderStatus.CANCELLED) {
                    txn.setStatus(TransactionStatus.FAILED);
                    String updatedNote = (txn.getNote() != null && !txn.getNote().isBlank())
                            ? txn.getNote() + " -> Đơn hàng đã hủy"
                            : "Đơn hàng đã hủy - Hủy thu tiền COD";
                    txn.setNote(updatedNote);
                    transactionRepository.save(txn);
                    log.info("Tự động đồng bộ bút toán {} của đơn {} sang FAILED", txn.getTransactionCode(), order.getId());
                }
            }
        }

        return txns.stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
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

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void completePendingPayment(Order order) {
        if (order == null || order.getId() == null) {
            return;
        }

        List<PaymentTransaction> txns = transactionRepository.findByOrderIdOrderByCreatedAtDesc(order.getId());
        boolean hasPending = false;

        for (PaymentTransaction txn : txns) {
            if (txn.getTransactionType() == TransactionType.PAYMENT && txn.getStatus() == TransactionStatus.PENDING) {
                txn.setStatus(TransactionStatus.SUCCESS);
                String updatedNote = (txn.getNote() != null && !txn.getNote().isBlank())
                        ? txn.getNote() + " -> Đã thu tiền khi giao hàng thành công"
                        : "Đã thu tiền khi giao hàng thành công (COD)";
                txn.setNote(updatedNote);
                transactionRepository.save(txn);
                hasPending = true;
                log.info("Cập nhật thành công bút toán {} của đơn hàng {} sang SUCCESS", txn.getTransactionCode(), order.getId());
            }
        }

        // Nếu đơn COD chưa từng có bút toán nào ghi nhận trước đó
        if (!hasPending && txns.isEmpty() && order.getPaymentType() == PaymentType.COD) {
            recordTransaction(
                    order,
                    "COD-" + order.getId(),
                    null,
                    PaymentType.COD,
                    TransactionType.PAYMENT,
                    order.getTotal(),
                    TransactionStatus.SUCCESS,
                    null,
                    "Thu tiền COD thành công khi hoàn tất đơn hàng"
            );
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void failPendingPayment(Order order, String reason) {
        if (order == null || order.getId() == null) {
            return;
        }

        List<PaymentTransaction> txns = transactionRepository.findByOrderIdOrderByCreatedAtDesc(order.getId());
        for (PaymentTransaction txn : txns) {
            if (txn.getTransactionType() == TransactionType.PAYMENT && txn.getStatus() == TransactionStatus.PENDING) {
                txn.setStatus(TransactionStatus.FAILED);
                String note = "Đơn hàng đã bị hủy. Lý do: " + (reason != null && !reason.isBlank() ? reason : "Hủy đơn");
                txn.setNote(note);
                transactionRepository.save(txn);
                log.info("Cập nhật bút toán {} của đơn {} sang FAILED do đơn bị hủy", txn.getTransactionCode(), order.getId());
            }
        }
    }

    @Override
    @Transactional
    public void syncPendingTransactions() {
        try {
            int completedCount = transactionRepository.syncCompletedOrdersTransactions();
            int cancelledCount = transactionRepository.syncCancelledOrdersTransactions();
            if (completedCount > 0 || cancelledCount > 0) {
                log.info("Đã đồng bộ sổ cái bút toán thanh toán: {} sang SUCCESS, {} sang FAILED", completedCount, cancelledCount);
            }
        } catch (Exception ex) {
            log.error("Lỗi khi tự động đồng bộ sổ cái bút toán: {}", ex.getMessage());
        }
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
