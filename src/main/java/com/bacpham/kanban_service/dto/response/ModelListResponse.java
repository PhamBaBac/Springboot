package com.bacpham.kanban_service.dto.response;


import com.bacpham.kanban_service.entity.GeminiModel;

import java.util.List;

public record ModelListResponse(String object, List<GeminiModel> data) {
}
