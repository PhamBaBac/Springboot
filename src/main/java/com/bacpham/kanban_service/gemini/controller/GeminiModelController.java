// src/main/java/com/bacpham/kanban_service/gemini/controller/GeminiModelController.java
package com.bacpham.kanban_service.gemini.controller;

import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.response.AiChatResponse;
import com.bacpham.kanban_service.dto.response.ChatHistoryResponse;
import com.bacpham.kanban_service.entity.ChatHistory;
import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.gemini.service.GeminiService;
import com.bacpham.kanban_service.gemini.service.RecommendationService;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.repository.ChatHistoryRepository;
import com.bacpham.kanban_service.repository.UserRepository;
import com.bacpham.kanban_service.service.ChatHistoryService;
import com.bacpham.kanban_service.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Collections;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Slf4j
public class GeminiModelController {

    private final RecommendationService recommendationService;
    private final GeminiService geminiService;
    private final ObjectMapper objectMapper;
    private final ChatHistoryService chatHistoryService;
    private final UserRepository userRepository;
    @GetMapping("/recommendations")
    public ApiResponse<List<String>> getRecommendations(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        String json = recommendationService.getRecommendationsForUser(currentUser);

        try {
            List<String> recommendationList = objectMapper.readValue(json, new TypeReference<>() {});
            log.info("Received recommendations for user: {}", recommendationList);
            return ApiResponse.<List<String>>builder()
                    .result(recommendationList)
                    .message("success")
                    .build();
        } catch (JsonProcessingException e) {
            return ApiResponse.<List<String>>builder()
                    .result(Collections.emptyList())
                    .message("Failed to parse recommendations")
                    .build();
        }
    }


    @PostMapping("/chat/support")
    public ApiResponse<String> chatSupport(
            @AuthenticationPrincipal User currentUser,
            @RequestParam("message") String message,
            @RequestParam("userCreatedAt")
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime userCreatedAt
            ) {

        // Kiểm tra đăng nhập
        if (currentUser == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        // Validate input
        if (message == null || message.trim().isEmpty()) {
            throw new AppException(ErrorCode.INVALID_INPUT);
        }

        if (message.length() > 1000) {
            throw new AppException(ErrorCode.MESSAGE_TOO_LONG);
        }

        try {
            log.info("Chat support request from user: {}, message: {}", currentUser.getUsername(), message);

            AiChatResponse response = geminiService.support(message.trim());
            chatHistoryService.saveChat(currentUser, message.trim(), response.getMessage(), userCreatedAt, response.getAiCreatedAt());

            return ApiResponse.<String>builder()
                    .result(response.getMessage())
                    .message("success")
                    .build();

        } catch (Exception e) {
            log.error("Lỗi xử lý chat cho user: {}", currentUser.getUsername(), e);
        }

        return ApiResponse.<String>builder()
                .result("Đã xảy ra lỗi trong quá trình xử lý yêu cầu của bạn. Vui lòng thử lại sau.")
                .message("error")
                .build();
    }
    @GetMapping("/chat/history")
    public ApiResponse<List<ChatHistoryResponse>> getChatHistory(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        String userId = user.getId();
        List<ChatHistoryResponse> history = chatHistoryService.getUserChatHistory(userId);

        return ApiResponse.<List<ChatHistoryResponse>>builder()
                .result(history)
                .build();
    }

    @DeleteMapping("/chat/history")
    public ApiResponse<String> deleteChatHistory(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        String userId = user.getId();
        chatHistoryService.deleteAllChatHistory(userId);
        return ApiResponse.<String>builder()
                .result("Chat history deleted successfully")
                .message("success")
                .build();
    }

}

