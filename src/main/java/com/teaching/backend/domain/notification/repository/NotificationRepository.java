package com.teaching.backend.domain.notification.repository;

import com.teaching.backend.domain.notification.entity.Notification;
import com.teaching.backend.domain.notification.enums.NotificationTargetType;
import com.teaching.backend.domain.notification.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findAllByUser_Id(Long userId, Sort sort);

    Page<Notification> findAllByUser_Id(Long userId, Pageable pageable);

    Optional<Notification> findByIdAndUser_Id(Long id, Long userId);

    @Query("""
            SELECT n FROM Notification n
            WHERE n.user.id = :userId
              AND n.createdAt >= :since
            ORDER BY
              CASE WHEN n.isRead = false THEN 0 ELSE 1 END ASC,
              n.createdAt DESC,
              n.id DESC
            """)
    List<Notification> findRecentByUserId(
            @Param("userId") Long userId,
            @Param("since") LocalDateTime since,
            Pageable pageable
    );

    @Query("""
            SELECT COUNT(n) FROM Notification n
            WHERE n.user.id = :userId
              AND n.isRead = false
              AND n.createdAt >= :since
            """)
    long countRecentUnreadByUserId(
            @Param("userId") Long userId,
            @Param("since") LocalDateTime since
    );

    boolean existsByUser_IdAndTargetTypeAndTargetIdAndNotificationTypeAndCreatedAtGreaterThanEqual(
            Long userId,
            NotificationTargetType targetType,
            Long targetId,
            NotificationType notificationType,
            LocalDateTime createdAt
    );
}
