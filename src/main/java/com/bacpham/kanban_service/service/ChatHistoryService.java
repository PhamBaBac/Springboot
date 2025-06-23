package com.bacpham.kanban_service.service;

import com.bacpham.kanban_service.dto.response.ChatHistoryResponse;
import com.bacpham.kanban_service.entity.ChatHistory;
import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.mapper.ChatHistoryMapper;
import com.bacpham.kanban_service.repository.CategoryRepository;
import com.bacpham.kanban_service.repository.ChatHistoryRepository;
import com.bacpham.kanban_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatHistoryService {

    private final ChatHistoryRepository chatHistoryRepository;
    private final UserRepository userRepository;
    private final ChatHistoryMapper chatHistoryMapper;

    public void saveChat(User user, String userMessage, String aiResponse, LocalDateTime userCreatedAt, LocalDateTime aiCreatedAt) {
        ChatHistory history = ChatHistory.builder()
                .user(user)
                .userMessage(userMessage)
                .userCreatedAt(userCreatedAt)
                .aiCreatedAt(aiCreatedAt)
                .aiResponse(aiResponse)
                .build();
        chatHistoryRepository.save(history);
    }

    public List<ChatHistoryResponse> getUserChatHistory(String userId) {
        log.info("Fetching chat history for userId: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        return chatHistoryRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(chatHistoryMapper::toChatHistoryResponse)
                .collect(Collectors.toList());
    }

    //delete chat history by id
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

