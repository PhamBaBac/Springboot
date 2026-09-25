package com.bacpham.kanban_service.repository;

import com.bacpham.kanban_service.entity.PaymentTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, String> {
    List<PaymentTransaction> findByOrderIdOrderByCreatedAtDesc(String orderId);

    Optional<PaymentTransaction> findByTransactionCode(String transactionCode);

    boolean existsByTransactionCode(String transactionCode);

    Page<PaymentTransaction> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
