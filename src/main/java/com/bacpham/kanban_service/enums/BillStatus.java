package com.bacpham.kanban_service.enums;

public enum BillStatus {
    PENDING, // Bill is created but not yet paid
    PAID, // Bill has been paid
    COMPLETED, // Bill has been completed
    CANCELLED, // Bill has been cancelled
    REFUNDED // Bill has been refunded
}
