package com.bacpham.kanban_service.dto.response;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record ModerationResult(
        @JsonPropertyDescription("Kết quả kiểm duyệt. 'true' nếu nội dung an toàn, 'false' nếu có vi phạm.")
        boolean safe
) {}