package com.dataguard.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "radar_messages", indexes = @Index(name = "idx_radar_message_conversation_created", columnList = "conversation_id, created_at"))
public class RadarMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private RadarConversation conversation;

    @Column(nullable = false, length = 16)
    private String role;

    @Column(nullable = false, length = 12000)
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected RadarMessage() {}

    public RadarMessage(String role, String content) { this.role = role; this.content = content; }
    public Long getId() { return id; }
    public RadarConversation getConversation() { return conversation; }
    public void setConversation(RadarConversation conversation) { this.conversation = conversation; }
    public String getRole() { return role; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}
