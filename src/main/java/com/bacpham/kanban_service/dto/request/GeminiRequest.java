package com.bacpham.kanban_service.dto.request;

import java.util.List;

public record GeminiRequest(List<Content> contents) {
    public record Content(List<Part> parts) {}
    public record Part(String text) {}

    public static GeminiRequest fromText(String text) {
        Part part = new Part(text);
        Content content = new Content(List.of(part));
        return new GeminiRequest(List.of(content));
    }
}