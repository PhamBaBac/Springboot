package com.bacpham.kanban_service.service.impl;

import com.bacpham.kanban_service.dto.request.ConversationRequest;
import com.bacpham.kanban_service.dto.response.ConversationResponse;
import com.bacpham.kanban_service.entity.Conversation;
import com.bacpham.kanban_service.entity.ParticipantInfo;
import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.mapper.ConversationMapper;
import com.bacpham.kanban_service.repository.ConversationRepository;
import com.bacpham.kanban_service.repository.UserRepository;
import com.bacpham.kanban_service.service.IConversationService;
import com.bacpham.kanban_service.service.UserService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ConversationServiceImpl implements IConversationService {
    ConversationRepository conversationRepository;
    ConversationMapper conversationMapper;
    UserRepository userRepository;
    @Override
    public ConversationResponse createConversation(ConversationRequest request) {
        List<String> participantIds = request.getParticipantIds();
        if (participantIds == null || participantIds.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_INPUT);
        }

        // Sort participants để sinh hash đồng nhất
        List<String> sortedIds = new ArrayList<>(participantIds);
        Collections.sort(sortedIds);
        String participantsHash = DigestUtils.md5Hex(String.join(",", sortedIds));

        Conversation conversation = conversationRepository.findByParticipantsHash(participantsHash)
                .orElseGet(() -> {
                    // Lấy thông tin từng participant từ UserService
                    List<ParticipantInfo> participantInfos = sortedIds.stream().map(id -> {
                        User user = userRepository.findById(id)
                                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
                        return ParticipantInfo.builder()
                                .userId(user.getId())
                                .username(user.getUsername())
                                .firstName(user.getFirstname())
                                .lastName(user.getLastname())
                                .avatar(user.getAvatarUrl())
                                .build();
                    }).toList();

                    Conversation newConversation = Conversation.builder()
                            .participants(participantInfos) // <-- thay vì List<String>, lưu List<ParticipantInfo>
                            .participantsHash(participantsHash)
                            .type(request.getType())
                            .createdDate(Instant.now())
                            .modifiedDate(Instant.now())
                            .build();

                    return conversationRepository.save(newConversation);
                });

        return conversationMapper.toConversationResponse(conversation);
    }


    @Override
    public List<ConversationResponse> getMyConversations() {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();

        List<Conversation> conversations = conversationRepository.findAllByParticipantsContains(userId);
        conversations.sort(Comparator.comparing(Conversation::getModifiedDate).reversed());

        return conversationMapper.toConversationResponseList(conversations);
    }
}
