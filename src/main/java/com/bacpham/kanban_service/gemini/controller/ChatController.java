package com.bacpham.kanban_service.gemini.controller;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.request.ChatRequest; // Import DTO mới
import com.bacpham.kanban_service.dto.response.AiChatResponse;
import com.bacpham.kanban_service.dto.response.ChatHistoryResponse;
import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.gemini.dto.AiGenerateRequest;
import com.bacpham.kanban_service.gemini.service.GeminiService;
import com.bacpham.kanban_service.gemini.service.SupportService;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.repository.UserRepository;
import com.bacpham.kanban_service.service.ChatHistoryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final SupportService supportService;
    private final ChatHistoryService chatHistoryService;
    private final UserRepository userRepository;
    private final GeminiService geminiService;

    @PostMapping("/generate")
    public ApiResponse<String> generateContent(@RequestBody AiGenerateRequest request) {
        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            throw new AppException(ErrorCode.INVALID_INPUT);
        }
        String content = geminiService.generateAutoContent(
                request.getType(),
                request.getTitle(),
                request.getContext()
        );
        return ApiResponse.<String>builder()
                .data(content)
                .message("success")
                .build();
    }

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