package com.teaching.backend.domain.user.controller;

import com.teaching.backend.domain.user.code.UserSuccessCode;
import com.teaching.backend.domain.user.dto.NotificationUpdateRequestDto;
import com.teaching.backend.domain.user.dto.NotificationUpdateResponseDto;
import com.teaching.backend.domain.user.dto.UserInfoResponseDto;
import com.teaching.backend.domain.user.dto.UserUpdateRequestDto;
import com.teaching.backend.domain.user.dto.UserUpdateResponseDto;
import com.teaching.backend.domain.user.service.UserService;
import com.teaching.backend.global.response.ApiResponse;
import com.teaching.backend.global.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 마이페이지 API.
 *
 * 현재 사용자 id 는 CurrentUserProvider 에서 가져온다(인증 미구현이라 개발용은 고정 id=1).
 * 성공 응답 code/message 는 스펙 문서에 정의된 엔드포인트별 UserSuccessCode 를 사용한다.
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final CurrentUserProvider currentUserProvider;

    /** [GET] /users/me — 내 정보 조회 */
    @GetMapping("/me")
    public ApiResponse<UserInfoResponseDto> getMyInfo() {
        Long userId = currentUserProvider.getCurrentUserId();
        return ApiResponse.onSuccess(UserSuccessCode.USER_INFO_FOUND, userService.getMyInfo(userId));
    }

    /** [PATCH] /users/me — 프로필 부분 수정 */
    @PatchMapping("/me")
    public ApiResponse<UserUpdateResponseDto> updateProfile(@RequestBody UserUpdateRequestDto request) {
        Long userId = currentUserProvider.getCurrentUserId();
        return ApiResponse.onSuccess(UserSuccessCode.PROFILE_UPDATED, userService.updateProfile(userId, request));
    }

    /** [PATCH] /users/me/notifications — 알림 수신 설정 변경 */
    @PatchMapping("/me/notifications")
    public ApiResponse<NotificationUpdateResponseDto> updateNotification(@RequestBody NotificationUpdateRequestDto request) {
        Long userId = currentUserProvider.getCurrentUserId();
        return ApiResponse.onSuccess(UserSuccessCode.NOTIFICATION_UPDATED, userService.updateNotification(userId, request));
    }
}
