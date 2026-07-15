package com.teaching.backend.user.controller;

import com.teaching.backend.global.response.ApiResponse;
import com.teaching.backend.global.response.SuccessCode;
import com.teaching.backend.global.security.CurrentUserProvider;
import com.teaching.backend.user.dto.NotificationUpdateRequestDto;
import com.teaching.backend.user.dto.NotificationUpdateResponseDto;
import com.teaching.backend.user.dto.UserInfoResponseDto;
import com.teaching.backend.user.dto.UserUpdateRequestDto;
import com.teaching.backend.user.dto.UserUpdateResponseDto;
import com.teaching.backend.user.service.UserService;
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
 * 성공 응답 code/message 는 스펙 문서에 정의된 엔드포인트별 SuccessCode 를 사용한다.
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
        return ApiResponse.onSuccess(SuccessCode.USER_INFO_FOUND, userService.getMyInfo(userId));
    }

    /** [PATCH] /users/me — 프로필 부분 수정 */
    @PatchMapping("/me")
    public ApiResponse<UserUpdateResponseDto> updateProfile(@RequestBody UserUpdateRequestDto request) {
        Long userId = currentUserProvider.getCurrentUserId();
        return ApiResponse.onSuccess(SuccessCode.PROFILE_UPDATED, userService.updateProfile(userId, request));
    }

    /** [PATCH] /users/me/notifications — 알림 수신 설정 변경 */
    @PatchMapping("/me/notifications")
    public ApiResponse<NotificationUpdateResponseDto> updateNotification(@RequestBody NotificationUpdateRequestDto request) {
        Long userId = currentUserProvider.getCurrentUserId();
        return ApiResponse.onSuccess(SuccessCode.NOTIFICATION_UPDATED, userService.updateNotification(userId, request));
    }
}
