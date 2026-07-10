package com.teaching.backend.chat.entity;

import com.teaching.backend.material.entity.MaterialChunk;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "chat_sources")
public class ChatSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_chunk_id", nullable = false)
    private MaterialChunk materialChunk;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_message_id", nullable = false)
    private ChatMessage chatMessage;

    @Lob
    @Column(nullable = false)
    private String citedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder(access = AccessLevel.PRIVATE)
    private ChatSource(MaterialChunk materialChunk, ChatMessage chatMessage, String citedAt) {
        this.materialChunk = materialChunk;
        this.chatMessage = chatMessage;
        this.citedAt = citedAt;
    }

    public static ChatSource create(MaterialChunk materialChunk, ChatMessage chatMessage, String citedAt) {
        return ChatSource.builder()
                .materialChunk(materialChunk)
                .chatMessage(chatMessage)
                .citedAt(citedAt)
                .build();
    }
}
