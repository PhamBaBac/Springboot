package com.bacpham.kanban_service.enums;

public enum TransactionStatus {
    PENDING,  // Đang chờ xử lý / Chờ thu tiền COD
    SUCCESS,  // Giao dịch thành công
    FAILED    // Giao dịch thất bại / Lỗi thanh toán
}
