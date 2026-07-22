package com.teaching.backend.domain.notification.controller;

import com.teaching.backend.domain.notification.code.NotificationSuccessCode;
import com.teaching.backend.domain.notification.dto.NotificationListResponse;
import com.teaching.backend.domain.notification.service.NotificationService;
import com.teaching.backend.global.apiPayload.code.GlobalErrorCode;
import com.teaching.backend.global.exception.GeneralException;
import com.teaching.backend.global.response.ApiResponse;
import com.teaching.backend.global.security.entity.AuthMember;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

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

    private Long getAuthenticatedUserId(AuthMember authMember) {
        if (authMember == null) {
            throw new GeneralException(GlobalErrorCode.UNAUTHORIZED);
        }

        return authMember.getUserId();
    }
}
