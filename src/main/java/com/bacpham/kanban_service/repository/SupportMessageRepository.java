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

    Optional<SupportMessage> findFirstByConversationIdOrderByCreatedAtDesc(String conversationId);

    Optional<SupportMessage> findFirstByConversationIdAndRoleOrderByCreatedAtAsc(String conversationId, com.bacpham.kanban_service.enums.Role role);

    @Query("SELECT COUNT(s) FROM SupportMessage s WHERE s.conversationId = :conversationId AND s.role = com.bacpham.kanban_service.enums.Role.USER AND s.status != com.bacpham.kanban_service.enums.MessageStatus.READ")
    Long countUnreadCustomerMessages(String conversationId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE SupportMessage s SET s.status = com.bacpham.kanban_service.enums.MessageStatus.READ WHERE s.conversationId = :conversationId AND s.role = com.bacpham.kanban_service.enums.Role.USER AND s.status != com.bacpham.kanban_service.enums.MessageStatus.READ")
    int markConversationAsRead(String conversationId);
}