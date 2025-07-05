package com.bacpham.kanban_service.service;

import com.bacpham.kanban_service.dto.request.ConversationRequest;
import com.bacpham.kanban_service.dto.response.ConversationResponse;

import java.util.List;

public interface IConversationService {
    ConversationResponse createConversation(ConversationRequest request);
    List<ConversationResponse> getMyConversations ();
}
