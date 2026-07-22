package com.teaching.backend.domain.user.controller;

import com.teaching.backend.domain.user.code.UserSuccessCode;
import com.teaching.backend.domain.user.dto.NotificationUpdateRequestDto;
import com.teaching.backend.domain.user.dto.NotificationUpdateResponseDto;
import com.teaching.backend.domain.user.dto.TeacherPersonaUpdateRequestDto;
import com.teaching.backend.domain.user.dto.TeacherPersonaUpdateResponseDto;
import com.teaching.backend.domain.user.dto.UserInfoResponseDto;
import com.teaching.backend.domain.user.dto.UserUpdateRequestDto;
import com.teaching.backend.domain.user.dto.UserUpdateResponseDto;
import com.teaching.backend.domain.user.dto.UserWithdrawRequestDto;
import com.teaching.backend.domain.user.service.UserService;
import com.teaching.backend.global.response.ApiResponse;
import com.teaching.backend.global.security.CurrentUserProvider;
import com.teaching.backend.global.security.util.RefreshTokenCookieUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 마이페이지 API.
 *
 * 현재 사용자 id 는 CurrentUserProvider(SecurityContext 기반)에서 가져온다.
 * 성공 응답 code/message 는 스펙 문서에 정의된 엔드포인트별 UserSuccessCode 를 사용한다.
 */
@Tag(name = "User", description = "마이페이지(내 정보/프로필/알림/AI 선생님 설정/회원 탈퇴) 관련 API")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final CurrentUserProvider currentUserProvider;

    @Value("${cookie.secure}")
    private boolean cookieSecure;

    /** [GET] /users/me — 내 정보 조회 */
    @Operation(
            summary = "내 정보 조회",
            description = "마이페이지 진입 시 내 프로필, 알림 설정, 연동된 소셜 계정 목록, AI 선생님 설정을 조회합니다."
    )
    @GetMapping("/me")
    public ApiResponse<UserInfoResponseDto> getMyInfo() {
        Long userId = currentUserProvider.getCurrentUserId();
        return ApiResponse.onSuccess(UserSuccessCode.USER_INFO_FOUND, userService.getMyInfo(userId));
    }

    /** [PATCH] /users/me — 프로필 부분 수정 */
    @Operation(
            summary = "프로필 수정",
            description = "닉네임, 프로필 이미지 URL 중 전달된 필드만 부분 수정합니다."
    )
    @PatchMapping("/me")
    public ApiResponse<UserUpdateResponseDto> updateProfile(@RequestBody UserUpdateRequestDto request) {
        Long userId = currentUserProvider.getCurrentUserId();
        return ApiResponse.onSuccess(UserSuccessCode.PROFILE_UPDATED, userService.updateProfile(userId, request));
    }

    /** [PATCH] /users/me/notifications — 알림 수신 설정 변경 */
    @Operation(
            summary = "알림 설정 변경",
            description = "푸시 알림 수신 여부를 토글합니다."
    )
    @PatchMapping("/me/notifications")
    public ApiResponse<NotificationUpdateResponseDto> updateNotification(@RequestBody NotificationUpdateRequestDto request) {
        Long userId = currentUserProvider.getCurrentUserId();
        return ApiResponse.onSuccess(UserSuccessCode.NOTIFICATION_UPDATED, userService.updateNotification(userId, request));
    }

    /** [DELETE] /users/me — 회원 탈퇴 */
    @Operation(
            summary = "회원 탈퇴",
            description = "탈퇴 사유를 받아 이력으로 남기고 계정을 소프트 삭제한 뒤, refreshToken을 무효화하고 쿠키를 만료시킵니다."
    )
    @DeleteMapping("/me")
    public ApiResponse<Void> withdraw(@RequestBody UserWithdrawRequestDto request, HttpServletResponse response) {
        Long userId = currentUserProvider.getCurrentUserId();
        userService.withdraw(userId, request);
        RefreshTokenCookieUtil.clear(response, cookieSecure);
        return ApiResponse.onSuccess(UserSuccessCode.WITHDRAWN, null);
    }

    /** [PATCH] /users/me/teacher-persona — AI 선생님 설정(페르소나) 변경 */
    @Operation(
            summary = "AI 선생님 설정(페르소나) 변경",
            description = "티칭맵에서 사용할 AI 선생님의 말투/성향(FRIENDLY/STRICT/CHEERING)을 설정합니다."
    )
    @PatchMapping("/me/teacher-persona")
    public ApiResponse<TeacherPersonaUpdateResponseDto> updateTeacherPersona(@RequestBody(required = false) TeacherPersonaUpdateRequestDto request) {
        Long userId = currentUserProvider.getCurrentUserId();
        return ApiResponse.onSuccess(UserSuccessCode.TEACHER_PERSONA_UPDATED, userService.updateTeacherPersona(userId, request));
    }
}
