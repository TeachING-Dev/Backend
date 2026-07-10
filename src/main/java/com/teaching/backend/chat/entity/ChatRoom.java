package com.teaching.backend.chat.entity;

import com.teaching.backend.global.common.BaseSoftDeleteEntity;
import com.teaching.backend.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "chat_rooms")
public class ChatRoom extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 15)
    private String title;

    private LocalDateTime lastMessageAt;

    @Builder(access = AccessLevel.PRIVATE)
    private ChatRoom(User user, String title) {
        this.user = user;
        this.title = title;
    }

    public static ChatRoom create(User user, String title) {
        return ChatRoom.builder()
                .user(user)
                .title(title)
                .build();
    }

    public void updateLastMessageAt(LocalDateTime lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
    }
}
