package com.dataguard.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ai_conversation_messages")
public class AIConversationMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private AIConversationSession session;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public AIConversationSession getSession() { return session; }
    public String getRole() { return role; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
    public void setSession(AIConversationSession session) { this.session = session; }
    public void setRole(String role) { this.role = role; }
    public void setContent(String content) { this.content = content; }
}
