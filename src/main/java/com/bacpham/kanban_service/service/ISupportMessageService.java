package com.bacpham.kanban_service.service;

import com.bacpham.kanban_service.dto.request.SupportMessageRequest;
import com.bacpham.kanban_service.dto.response.SupportMessageResponse;
import com.bacpham.kanban_service.enums.MessageStatus;
import com.bacpham.kanban_service.enums.Role;
import com.bacpham.kanban_service.model.SupportMessage;

import java.util.List;

public interface ISupportMessageService {

    /**
     * Lưu tin nhắn mới hoặc cập nhật tin nhắn
     */
    SupportMessage saveMessage(SupportMessageRequest supportMessageRequest);

    List<SupportMessageResponse> getConversation(String conversationId);
    List<String> getActiveConversations();
}
