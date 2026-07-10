package com.teaching.backend.domain;


import com.teaching.backend.domain.common.BaseSoftDeleteEntity;
import com.teaching.backend.domain.enums.NotificationTargetType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name="notifications")
public class Notification extends BaseSoftDeleteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="user_id",nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String title;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private Boolean isRead;

    // 알림 클릭 시 이동할 대상 종류 (없을 수도 있음 - 단순 공지 등)
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private NotificationTargetType targetType;

    // 대상 종류에 해당하는 엔티티의 id
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

    // 이동 대상이 있는 알림 (예: 티칭맵 완료 알림)
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

    // 이동 대상 없는 단순 알림 (예: 공지사항)
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
