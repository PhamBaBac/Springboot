package com.bacpham.kanban_service.enums;

public enum TransactionType {
    PAYMENT,        // Thanh toán đơn hàng (chiều dương vào hệ thống)
    REFUND,         // Hoàn tiền đơn hàng (chiều âm hoàn lại cho khách)
    COD_COLLECTION, // Đơn vị vận chuyển (shipper) thu tiền COD
    ADJUSTMENT      // Điều chỉnh kế toán đối soát
}
