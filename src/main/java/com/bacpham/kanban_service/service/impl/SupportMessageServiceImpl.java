package com.bacpham.kanban_service.service.impl;

import com.bacpham.kanban_service.dto.request.SupportMessageRequest;
import com.bacpham.kanban_service.dto.response.SupportMessageResponse;
import com.bacpham.kanban_service.enums.MessageStatus;
import com.bacpham.kanban_service.enums.Role;
import com.bacpham.kanban_service.mapper.SupportMessageMapper;
import com.bacpham.kanban_service.model.SupportMessage;
import com.bacpham.kanban_service.repository.SupportMessageRepository;
import com.bacpham.kanban_service.service.ISupportMessageService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class SupportMessageServiceImpl implements ISupportMessageService {

    SupportMessageRepository repository;
    SupportMessageMapper mapper;
    @Override
    public SupportMessage saveMessage(SupportMessageRequest request) {
        // Tạo conversationId chung
        String conversationId = generateConversationId(request);

        SupportMessage message = SupportMessage.builder()
                .conversationId(conversationId)
                .senderId(request.senderId())
                .receiverId(request.receiverId())
                .role(request.role())
                .content(request.content())
                .avatar(request.avatar())
                .username(request.username())
                .status(MessageStatus.PENDING)
                .build();
        return repository.save(message);
    }

    @Override
    public List<SupportMessageResponse> getConversation(String conversationId) {
        repository.findByConversationIdOrderByCreatedAtAsc(conversationId);

        return repository.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .map(mapper::toSupportMessageResponse)
                .toList();
    }

    /**
     * Tạo conversationId chung cho cả admin và user
     * - User gửi: "user_{userId}"
     * - Admin gửi: tìm conversationId từ tin nhắn trước đó hoặc "user_{receiverId}"
     */
    private String generateConversationId(SupportMessageRequest request) {
        if (request.role() == Role.USER) {
            return "user_" + request.senderId();
        }

        if (request.role() == Role.ADMIN && request.receiverId() != null) {
            Optional<SupportMessage> lastMessage = repository
                    .findFirstBySenderIdOrReceiverIdOrderByCreatedAtDesc(
                            request.receiverId(), request.receiverId());

            if (lastMessage.isPresent()) {
                return lastMessage.get().getConversationId();
            } else {
                return "user_" + request.receiverId();
            }
        }

        return "conv_" + request.senderId();
    }

    public List<String> getActiveConversations() {
        return repository.findLatestConversations();
    }
}