package com.bacpham.kanban_service.gemini.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Part(String text, InlineData inlineData) {
    public static Part fromText(String text) {
        return new Part(text, null);
    }

    public static Part fromImage(String mimeType, String base64Data) {
        return new Part(null, new InlineData(mimeType, base64Data));
    }
}
