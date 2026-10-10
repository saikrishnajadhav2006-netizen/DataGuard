package com.dataguard.repository;

import com.dataguard.entity.AIConversationSession;
import com.dataguard.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface AIConversationSessionRepository extends JpaRepository<AIConversationSession, Long> {
    Optional<AIConversationSession> findBySessionIdAndUser(UUID sessionId, User user);
    Optional<AIConversationSession> findFirstByUserOrderByUpdatedAtDesc(User user);
}
