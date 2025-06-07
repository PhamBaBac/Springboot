package com.bacpham.kanban_service.gemini.dto;

import java.util.List;

public record GeminiImageRequest(List<Content> contents) {
    public static GeminiImageRequest fromParts(List<Part> parts) {
        return new GeminiImageRequest(List.of(new Content(parts)));
    }
}
