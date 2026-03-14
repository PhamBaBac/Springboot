package com.bacpham.kanban_service.enums;

public enum MessageStatus {
    PENDING,   // User gửi, chưa có admin trả lời
    SENT,      // Đã gửi tới admin
    ANSWERED,  // Admin đã trả lời
    READ       // User đã đọc
}
