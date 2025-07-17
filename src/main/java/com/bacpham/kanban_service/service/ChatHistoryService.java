package com.bacpham.kanban_service.service;

import com.bacpham.kanban_service.dto.response.ChatHistoryResponse;
import com.bacpham.kanban_service.entity.ChatHistory;
import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.repository.ChatHistoryRepository;
import com.bacpham.kanban_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatHistoryService {

    private final ChatHistoryRepository chatHistoryRepository;
    private final UserRepository userRepository;

    public List<ChatHistoryResponse> getUserChatHistory(String userId) {
        return chatHistoryRepository.findAllByUserIdOrderByCreatedAtAsc(userId)
                .stream()
                .map(chat -> ChatHistoryResponse.builder()
                        .role(chat.getRole())
                        .message(chat.getMessage())
                        .createdAt(chat.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    public void saveNewMessage(User user, String message, String role) {
        ChatHistory chatHistory = ChatHistory.builder()
                .user(user)
                .message(message)
                .role(role)
                .build();
        chatHistoryRepository.save(chatHistory);
    }

    public void deleteAllChatHistory(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        List<ChatHistory> chatHistories = chatHistoryRepository.findByUserOrderByCreatedAtDesc(user);
        if (chatHistories.isEmpty()) {
            throw new AppException(ErrorCode.CHAT_HISTORY_NOT_FOUND);
        }
        chatHistoryRepository.deleteAll(chatHistories);
    }
}