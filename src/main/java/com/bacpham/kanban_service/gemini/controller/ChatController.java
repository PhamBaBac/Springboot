package com.bacpham.kanban_service.gemini.controller;

import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.request.ChatRequest; // Import DTO mới
import com.bacpham.kanban_service.dto.response.AiChatResponse;
import com.bacpham.kanban_service.dto.response.ChatHistoryResponse;
import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.gemini.service.SupportService;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.repository.UserRepository;
import com.bacpham.kanban_service.service.ChatHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final SupportService supportService;
    private final ChatHistoryService chatHistoryService;
    private final UserRepository userRepository;

    @PostMapping("/chat/support")
    public ApiResponse<AiChatResponse> chatSupport(
            @AuthenticationPrincipal User currentUser,
            @RequestBody ChatRequest request,
            final Authentication  principal
    ) {
        if (currentUser == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        String newMessageContent = request.getMessage();
        if (newMessageContent == null || newMessageContent.trim().isEmpty()) {
            throw new AppException(ErrorCode.INVALID_INPUT);
        }
        AiChatResponse aiResponse = supportService.supportWithHistory(request, getUserId(principal));

        chatHistoryService.saveNewMessage(currentUser, newMessageContent, "USER");
        chatHistoryService.saveNewMessage(currentUser, aiResponse.getMessage(), "ASSISTANT");

        return ApiResponse.<AiChatResponse>builder()
                .data(aiResponse)
                .message("success")
                .build();
    }

    @GetMapping("/chat/history")
    public ApiResponse<List<ChatHistoryResponse>> getChatHistory(final  Authentication principal) {

        return ApiResponse.<List<ChatHistoryResponse>>builder()
                .data(chatHistoryService.getUserChatHistory(getUserId(principal)))
                .build();
    }

    @DeleteMapping("/chat/history")
    public ApiResponse<String> deleteChatHistory( final Authentication  principal) {

        chatHistoryService.deleteAllChatHistory(getUserId(principal));

        return ApiResponse.<String>builder()
                .data("Chat history deleted successfully")
                .message("success")
                .build();
    }

    private String getUserId(final Authentication  principal) {
        return ((User) principal.getPrincipal()).getId();
    }
}