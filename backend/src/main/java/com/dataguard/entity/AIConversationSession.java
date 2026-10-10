package com.dataguard.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_conversation_sessions")
public class AIConversationSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID sessionId = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public UUID getSessionId() { return sessionId; }
    public User getUser() { return user; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUser(User user) { this.user = user; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
