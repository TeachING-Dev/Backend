package com.teaching.backend.domain.notification.service;

import com.teaching.backend.domain.notification.dto.NotificationListResponse;
import com.teaching.backend.domain.notification.entity.Notification;
import com.teaching.backend.domain.notification.repository.NotificationRepository;
import com.teaching.backend.global.apiPayload.code.GlobalErrorCode;
import com.teaching.backend.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationRepository notificationRepository;

    public List<NotificationListResponse> getNotifications(Long userId, Integer size) {
        validateUserId(userId);
        validateSize(size);

        return findNotifications(userId, size)
                .stream()
                .map(NotificationListResponse::from)
                .toList();
    }

    private List<Notification> findNotifications(Long userId, Integer size) {
        Sort recentSort = recentSort();
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : size;

        return notificationRepository.findAllByUser_Id(
                userId,
                PageRequest.of(0, pageSize, recentSort)
        ).getContent();
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new GeneralException(GlobalErrorCode.UNAUTHORIZED);
        }
    }

    private void validateSize(Integer size) {
        if (size != null && (size <= 0 || size > MAX_PAGE_SIZE)) {
            throw new GeneralException(GlobalErrorCode.BAD_REQUEST);
        }
    }

    private Sort recentSort() {
        return Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")
        );
    }
}
