package com.teaching.backend.domain.home.controller;

import com.teaching.backend.domain.home.code.HomeSuccessCode;
import com.teaching.backend.domain.home.dto.HomeDashboardResponse;
import com.teaching.backend.domain.home.service.HomeService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Home", description = "홈 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/home")
public class HomeController {

    private final HomeService homeService;

    @Operation(
            summary = "홈 화면 조회",
            description = "최근 완료 자료 5개와 진행 중인 티칭맵 3개를 조회합니다."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<HomeDashboardResponse>> getDashboard(
            @AuthenticationPrincipal AuthMember authMember
    ) {
        HomeDashboardResponse result = homeService.getDashboard(getAuthenticatedUserId(authMember));

        return ResponseEntity.ok(
                ApiResponse.onSuccess(HomeSuccessCode.HOME_DASHBOARD_SUCCESS, result)
        );
    }

    private Long getAuthenticatedUserId(AuthMember authMember) {
        if (authMember == null) {
            throw new GeneralException(GlobalErrorCode.UNAUTHORIZED);
        }

        return authMember.getUserId();
    }
}
