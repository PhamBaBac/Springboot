package com.bacpham.kanban_service.controller;

import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.request.SupportMessageRequest;
import com.bacpham.kanban_service.dto.response.SupportMessageResponse;
import com.bacpham.kanban_service.model.SupportMessage;
import com.bacpham.kanban_service.service.ISupportMessageService;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/support")
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = lombok.AccessLevel.PRIVATE)
@Slf4j
public class SupportMessageController {

    ISupportMessageService supportMessageService;

    // Lấy lịch sử tin nhắn theo conversationId
    @GetMapping("/historyMessage/{conversationId}")
    public ApiResponse<List<SupportMessageResponse>> getHistory(@PathVariable String conversationId) {
        List<SupportMessageResponse> history = supportMessageService.getConversation(conversationId);
        log.info("Lịch sử tin nhắn conversation {}: {} messages", conversationId, history.size());
        return ApiResponse.<List<SupportMessageResponse>>builder()
                .data(history)
                .build();
    }

    // Lấy tất cả conversations (cho admin)
    @GetMapping("/conversations")
    public ApiResponse<List<String>> getAllConversations() {
        List<String> conversations = supportMessageService.getActiveConversations();
        log.info("Lấy danh sách {} conversations", conversations.size());
        return ApiResponse.<List<String>>builder()
                .data(conversations)
                .build();
    }

    // Lấy lịch sử tin nhắn của user cụ thể (cho admin)
    @GetMapping("/user/{userId}")
    public ApiResponse<List<SupportMessageResponse>> getUserMessages(@PathVariable String userId) {
        String conversationId = "user_" + userId;
        List<SupportMessageResponse> messages = supportMessageService.getConversation(conversationId);
        return ApiResponse.<List<SupportMessageResponse>>builder()
                .data(messages)
                .build();
    }

    @PostMapping("/save")
    public ApiResponse<SupportMessage> saveMessage(@RequestBody SupportMessageRequest request) {
        SupportMessage savedMessage = supportMessageService.saveMessage(request);
        return ApiResponse.<SupportMessage>builder()
                .data(savedMessage)
                .build();
    }
}