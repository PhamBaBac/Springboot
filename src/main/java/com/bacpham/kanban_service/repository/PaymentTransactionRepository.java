package com.bacpham.kanban_service.repository;

import com.bacpham.kanban_service.entity.PaymentTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, String> {
    List<PaymentTransaction> findByOrderIdOrderByCreatedAtDesc(String orderId);

    Optional<PaymentTransaction> findByTransactionCode(String transactionCode);

    boolean existsByTransactionCode(String transactionCode);

    Page<PaymentTransaction> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Modifying
    @Query("UPDATE PaymentTransaction pt SET pt.status = com.bacpham.kanban_service.enums.TransactionStatus.SUCCESS, pt.note = 'Đã thu tiền COD khi hoàn thành đơn hàng' " +
           "WHERE pt.status = com.bacpham.kanban_service.enums.TransactionStatus.PENDING " +
           "AND pt.transactionType = com.bacpham.kanban_service.enums.TransactionType.PAYMENT " +
           "AND pt.order.orderStatus = com.bacpham.kanban_service.enums.OrderStatus.COMPLETED")
    int syncCompletedOrdersTransactions();

    @Modifying
    @Query("UPDATE PaymentTransaction pt SET pt.status = com.bacpham.kanban_service.enums.TransactionStatus.FAILED, pt.note = 'Hủy bút toán theo đơn hàng đã hủy' " +
           "WHERE pt.status = com.bacpham.kanban_service.enums.TransactionStatus.PENDING " +
           "AND pt.transactionType = com.bacpham.kanban_service.enums.TransactionType.PAYMENT " +
           "AND pt.order.orderStatus = com.bacpham.kanban_service.enums.OrderStatus.CANCELLED")
    int syncCancelledOrdersTransactions();
}
