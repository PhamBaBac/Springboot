package com.bacpham.kanban_service.mapper;

import com.bacpham.kanban_service.dto.response.ChatHistoryResponse;
import com.bacpham.kanban_service.entity.ChatHistory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ChatHistoryMapper {

     ChatHistoryResponse toChatHistoryResponse(ChatHistory chatHistory);
}
