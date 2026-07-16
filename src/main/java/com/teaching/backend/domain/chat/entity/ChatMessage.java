package com.teaching.backend.domain.chat.entity;

import com.teaching.backend.domain.chat.enums.ChatRole;
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
@Table(name = "chat_messages")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chatroom_id", nullable = false)
    private ChatRoom chatRoom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ChatRole role;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private Boolean isFallback;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder(access = AccessLevel.PRIVATE)
    private ChatMessage(ChatRoom chatRoom, ChatRole role, String content, Boolean isFallback) {
        this.chatRoom = chatRoom;
        this.role = role;
        this.content = content;
        this.isFallback = isFallback;
    }

    public static ChatMessage createUserMessage(ChatRoom chatRoom, String content) {
        return ChatMessage.builder()
                .chatRoom(chatRoom)
                .role(ChatRole.USER)
                .content(content)
                .isFallback(false)
                .build();
    }

    public static ChatMessage createAiMessage(ChatRoom chatRoom, String content) {
        return ChatMessage.builder()
                .chatRoom(chatRoom)
                .role(ChatRole.AI)
                .content(content)
                .isFallback(false)
                .build();
    }

    public static ChatMessage createAiFallbackMessage(ChatRoom chatRoom, String content) {
        return ChatMessage.builder()
                .chatRoom(chatRoom)
                .role(ChatRole.AI)
                .content(content)
                .isFallback(true)
                .build();
    }
}
