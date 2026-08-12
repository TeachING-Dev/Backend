package com.teaching.backend.domain.notification.controller;

import com.teaching.backend.domain.notification.code.NotificationSuccessCode;
import com.teaching.backend.domain.notification.dto.NotificationListResponse;
import com.teaching.backend.domain.notification.dto.NotificationReadResponse;
import com.teaching.backend.domain.notification.dto.NotificationSummaryResponse;
import com.teaching.backend.domain.notification.service.NotificationService;
import com.teaching.backend.global.apiPayload.code.GlobalErrorCode;
import com.teaching.backend.global.exception.GeneralException;
import com.teaching.backend.global.response.ApiResponse;
import com.teaching.backend.global.security.entity.AuthMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Notification", description = "알림 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(
            summary = "알림 목록 조회",
            description = "최근 30일 알림을 읽지 않은 알림 우선, 최신순으로 size만큼 조회합니다."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationListResponse>>> getNotifications(
            @AuthenticationPrincipal AuthMember authMember,
            @RequestParam(required = false) Integer size
    ) {
        List<NotificationListResponse> result = notificationService.getNotifications(
                getAuthenticatedUserId(authMember),
                size
        );

        return ResponseEntity.ok(
                ApiResponse.onSuccess(NotificationSuccessCode.NOTIFICATION_LIST_SUCCESS, result)
        );
    }

    @Operation(
            summary = "알림 요약 조회",
            description = "최근 30일 내 읽지 않은 알림 존재 여부와 개수를 조회합니다."
    )
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<NotificationSummaryResponse>> getNotificationSummary(
            @AuthenticationPrincipal AuthMember authMember
    ) {
        NotificationSummaryResponse result = notificationService.getNotificationSummary(
                getAuthenticatedUserId(authMember)
        );

        return ResponseEntity.ok(
                ApiResponse.onSuccess(NotificationSuccessCode.NOTIFICATION_SUMMARY_SUCCESS, result)
        );
    }

    @Operation(
            summary = "알림 읽음 처리",
            description = "특정 알림을 읽음 상태로 변경합니다."
    )
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<NotificationReadResponse>> markAsRead(
            @AuthenticationPrincipal AuthMember authMember,
            @PathVariable Long notificationId
    ) {
        NotificationReadResponse result = notificationService.markAsRead(
                getAuthenticatedUserId(authMember),
                notificationId
        );

        return ResponseEntity.ok(
                ApiResponse.onSuccess(NotificationSuccessCode.NOTIFICATION_READ_SUCCESS, result)
        );
    }

    private Long getAuthenticatedUserId(AuthMember authMember) {
        if (authMember == null) {
            throw new GeneralException(GlobalErrorCode.UNAUTHORIZED);
        }

        return authMember.getUserId();
    }
}
