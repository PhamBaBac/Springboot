package com.bacpham.kanban_service.strategy.payment;

import com.bacpham.kanban_service.enums.PaymentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class PaymentStrategyRegistryTest {

    private PaymentStrategyRegistry registry;
    private PaymentStrategy vnPayStrategy;
    private PaymentStrategy moMoStrategy;

    @BeforeEach
    void setUp() {
        vnPayStrategy = Mockito.mock(PaymentStrategy.class);
        when(vnPayStrategy.getPaymentType()).thenReturn(PaymentType.VNPAY);

        moMoStrategy = Mockito.mock(PaymentStrategy.class);
        when(moMoStrategy.getPaymentType()).thenReturn(PaymentType.MOMO);

        registry = new PaymentStrategyRegistry(List.of(vnPayStrategy, moMoStrategy));
    }

    @Test
    @DisplayName("Lấy strategy theo enum PaymentType hợp lệ")
    void testGetStrategyByEnum() {
        assertEquals(vnPayStrategy, registry.getStrategy(PaymentType.VNPAY));
        assertEquals(moMoStrategy, registry.getStrategy(PaymentType.MOMO));
    }

    @Test
    @DisplayName("Lấy strategy theo chuỗi ký tự (case-insensitive)")
    void testGetStrategyByString() {
        assertEquals(vnPayStrategy, registry.getStrategyOrDefault("vnpay"));
        assertEquals(vnPayStrategy, registry.getStrategyOrDefault("VNPAY"));
        assertEquals(moMoStrategy, registry.getStrategyOrDefault("momo"));
        assertEquals(moMoStrategy, registry.getStrategyOrDefault("MOMO"));
    }

    @Test
    @DisplayName("Fallback về VNPay khi tham số rỗng hoặc không xác định (đảm bảo tương thích ngược)")
    void testGetStrategyFallbackToVNPay() {
        assertEquals(vnPayStrategy, registry.getStrategyOrDefault(null));
        assertEquals(vnPayStrategy, registry.getStrategyOrDefault(""));
        assertEquals(vnPayStrategy, registry.getStrategyOrDefault("   "));
        assertEquals(vnPayStrategy, registry.getStrategyOrDefault("UNKNOWN_PAYMENT"));
    }

    @Test
    @DisplayName("Kiểm tra hasStrategy và getSupportedTypes")
    void testSupportedTypes() {
        assertTrue(registry.hasStrategy(PaymentType.VNPAY));
        assertTrue(registry.hasStrategy(PaymentType.MOMO));
        assertFalse(registry.hasStrategy(PaymentType.BANK_TRANSFER));

        assertEquals(2, registry.getSupportedTypes().size());
        assertTrue(registry.getSupportedTypes().contains(PaymentType.VNPAY));
        assertTrue(registry.getSupportedTypes().contains(PaymentType.MOMO));
    }
}
