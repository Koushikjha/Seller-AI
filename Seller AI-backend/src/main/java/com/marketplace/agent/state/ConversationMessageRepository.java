package com.marketplace.agent.state;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, UUID> {

    List<ConversationMessage> findByConversationIdOrderBySeqAsc(UUID conversationId);

    @Query("select coalesce(max(m.seq), -1) from ConversationMessage m where m.conversationId = :conversationId")
    int maxSeq(UUID conversationId);
}
