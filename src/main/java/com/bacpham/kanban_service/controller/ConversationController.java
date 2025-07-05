package com.bacpham.kanban_service.controller;


import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.request.ConversationRequest;
import com.bacpham.kanban_service.dto.response.ConversationResponse;
import com.bacpham.kanban_service.service.IConversationService;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(makeFinal = true, level = lombok.AccessLevel.PRIVATE)
public class ConversationController {
    IConversationService conversationService;
    @PostMapping
    public ApiResponse<ConversationResponse> createConversation(
            @RequestBody ConversationRequest request
    ) {
        ConversationResponse conversation = conversationService.createConversation(request);
        return ApiResponse.<ConversationResponse>builder()
                .result(conversation)
                .message("Conversation created or returned successfully")
                .build();
    }

    @GetMapping
    public ApiResponse<List<ConversationResponse>> getMyConversations() {
        List<ConversationResponse> conversations = conversationService.getMyConversations();
        return ApiResponse.<List<ConversationResponse>>builder()
                .result(conversations)
                .message("Fetched user conversations successfully")
                .build();
    }
}
