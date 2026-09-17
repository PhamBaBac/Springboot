package com.bacpham.kanban_service.repository;

import com.bacpham.kanban_service.entity.SupportMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupportMessageRepository extends JpaRepository<SupportMessage, String> {

    List<SupportMessage> findByConversationIdOrderByCreatedAtAsc(String conversationId);

    Optional<SupportMessage> findFirstBySenderIdOrReceiverIdOrderByCreatedAtDesc(String senderId, String receiverId);

    @Query("select sm.conversationId " +
            "from SupportMessage sm " +
            "group by sm.conversationId " +
            "order by max(sm.createdAt) desc")
    List<String> findLatestConversations();


    @Query("SELECT COUNT(s) FROM SupportMessage s WHERE s.conversationId = :conversationId AND s.status = 'DELIVERED' AND s.senderId != :userId")
    Long countUnreadMessages(String conversationId, String userId);
}