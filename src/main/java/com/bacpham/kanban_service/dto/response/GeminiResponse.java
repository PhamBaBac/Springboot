package com.bacpham.kanban_service.dto.response;

import java.util.List;
import java.util.Optional;

public record GeminiResponse(List<Candidate> candidates) {
    public record Candidate(Content content) {}
    public record Content(List<Part> parts) {}
    public record Part(String text) {}

    // Phương thức tiện ích để lấy text từ candidate đầu tiên một cách an toàn
    public Optional<String> getFirstCandidateText() {
        return Optional.ofNullable(candidates)
                .filter(c -> !c.isEmpty())
                .map(c -> c.get(0))
                .map(Candidate::content)
                .map(Content::parts)
                .filter(p -> !p.isEmpty())
                .map(p -> p.get(0))
                .map(Part::text);
    }
}