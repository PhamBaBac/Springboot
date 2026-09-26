package com.bacpham.kanban_service.service.impl;

import com.bacpham.kanban_service.dto.request.SupportMessageRequest;
import com.bacpham.kanban_service.dto.response.SupportMessageResponse;
import com.bacpham.kanban_service.enums.MessageStatus;
import com.bacpham.kanban_service.enums.Role;
import com.bacpham.kanban_service.mapper.SupportMessageMapper;
import com.bacpham.kanban_service.entity.SupportMessage;
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
        return repository.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .map(mapper::toSupportMessageResponse)
                .toList();
    }

    private String generateConversationId(SupportMessageRequest request) {
        if (request.conversationId() != null && !request.conversationId().isBlank()) {
            return request.conversationId();
        }

        if (request.role() == Role.USER) {
            return "user_" + request.senderId();
        }

        if ((request.role() == Role.ADMIN || request.role() == Role.MANAGER) && request.receiverId() != null) {
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

    @Override
    public List<String> getActiveConversations() {
        return repository.findLatestConversations();
    }

    @Override
    public List<com.bacpham.kanban_service.dto.response.ConversationSummaryResponse> getConversationSummaries() {
        List<String> conversationIds = repository.findLatestConversations();
        return conversationIds.stream().map(convId -> {
            Optional<SupportMessage> lastMsgOpt = repository.findFirstByConversationIdOrderByCreatedAtDesc(convId);
            Optional<SupportMessage> customerMsgOpt = repository.findFirstByConversationIdAndRoleOrderByCreatedAtAsc(convId, Role.USER);
            Long unreadCount = repository.countUnreadCustomerMessages(convId);

            String customerId = customerMsgOpt.map(SupportMessage::getSenderId)
                    .orElseGet(() -> convId.startsWith("user_") ? convId.substring(5) : convId);
            String customerName = customerMsgOpt.map(SupportMessage::getUsername)
                    .orElseGet(() -> lastMsgOpt.map(SupportMessage::getUsername).orElse("Khách hàng"));
            String customerAvatar = customerMsgOpt.map(SupportMessage::getAvatar)
                    .orElseGet(() -> lastMsgOpt.map(SupportMessage::getAvatar).orElse(null));

            return com.bacpham.kanban_service.dto.response.ConversationSummaryResponse.builder()
                    .conversationId(convId)
                    .customerId(customerId)
                    .customerName(customerName)
                    .customerAvatar(customerAvatar)
                    .lastMessage(lastMsgOpt.map(SupportMessage::getContent).orElse(""))
                    .lastMessageTime(lastMsgOpt.map(SupportMessage::getCreatedAt).orElse(null))
                    .lastSenderRole(lastMsgOpt.map(SupportMessage::getRole).orElse(Role.USER))
                    .unreadCount(unreadCount != null ? unreadCount : 0L)
                    .build();
        }).toList();
    }

    @Override
    public int markAsRead(String conversationId) {
        return repository.markConversationAsRead(conversationId);
    }
}