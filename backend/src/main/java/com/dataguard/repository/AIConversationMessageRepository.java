package com.dataguard.repository;

import com.dataguard.entity.AIConversationMessage;
import com.dataguard.entity.AIConversationSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AIConversationMessageRepository extends JpaRepository<AIConversationMessage, Long> {
    List<AIConversationMessage> findTop20BySessionOrderByCreatedAtDesc(AIConversationSession session);
    List<AIConversationMessage> findBySessionOrderByCreatedAtAsc(AIConversationSession session);
}
