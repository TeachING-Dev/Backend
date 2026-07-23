package com.teaching.backend.domain.home.controller;

import com.teaching.backend.domain.home.code.HomeSuccessCode;
import com.teaching.backend.domain.home.dto.HomeDashboardResponse;
import com.teaching.backend.domain.home.service.HomeService;
import com.teaching.backend.global.apiPayload.code.GlobalErrorCode;
import com.teaching.backend.global.exception.GeneralException;
import com.teaching.backend.global.response.ApiResponse;
import com.teaching.backend.global.security.entity.AuthMember;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/home")
public class HomeController {

    private final HomeService homeService;

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
