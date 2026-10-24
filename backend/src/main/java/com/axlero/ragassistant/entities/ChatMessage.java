
package com.axlero.ragassistant.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChatMessageRole role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private ChatSession session;

    protected ChatMessage() {
    }

    public ChatMessage(ChatMessageRole role, String content) {
        this.role = role;
        this.content = content;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public ChatMessageRole getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public ChatSession getSession() {
        return session;
    }

    void setSequenceNumber(int sequenceNumber) {
        if (sequenceNumber < 0) {
            throw new IllegalArgumentException(
                    "Sequence number cannot be negative.");
        }
        this.sequenceNumber = sequenceNumber;
    }

    void setSession(ChatSession session) {
        this.session = session;
    }
}
