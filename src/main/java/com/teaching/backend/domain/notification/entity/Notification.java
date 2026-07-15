package com.teaching.backend.domain.notification.entity;

import com.teaching.backend.global.common.BaseSoftDeleteEntity;
import com.teaching.backend.domain.notification.enums.NotificationTargetType;
import com.teaching.backend.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
@Table(name = "notifications")
public class Notification extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String title;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private Boolean isRead;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private NotificationTargetType targetType;

    private Long targetId;

    @Builder(access = AccessLevel.PRIVATE)
    private Notification(User user, String title, String content,
                         NotificationTargetType targetType, Long targetId) {
        this.user = user;
        this.title = title;
        this.content = content;
        this.isRead = false;
        this.targetType = targetType;
        this.targetId = targetId;
    }

    public static Notification createWithTarget(User user, String title, String content,
                                                NotificationTargetType targetType, Long targetId) {
        return Notification.builder()
                .user(user)
                .title(title)
                .content(content)
                .targetType(targetType)
                .targetId(targetId)
                .build();
    }

    public static Notification createSimple(User user, String title, String content) {
        return Notification.builder()
                .user(user)
                .title(title)
                .content(content)
                .build();
    }

    public void markAsRead() {
        this.isRead = true;
    }
}
