package com.bacpham.kanban_service.gemini.dto;

public record BillItem(String itemName,
                       String unit,
                       Integer quantity,
                       Double price,
                       Double subTotal
) {
}