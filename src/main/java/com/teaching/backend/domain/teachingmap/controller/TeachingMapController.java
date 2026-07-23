package com.teaching.backend.domain.teachingmap.controller;

import com.teaching.backend.domain.teachingmap.code.TeachingMapSuccessCode;
import com.teaching.backend.domain.teachingmap.dto.TeachingMapListResponse;
import com.teaching.backend.domain.teachingmap.enums.TeachingMapStatus;
import com.teaching.backend.domain.teachingmap.service.TeachingMapService;
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
@RequestMapping("/api/v1/teaching-maps")
public class TeachingMapController {

    private final TeachingMapService teachingMapService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TeachingMapListResponse>>> getTeachingMaps(
            @AuthenticationPrincipal AuthMember authMember,
            @RequestParam(required = false) TeachingMapStatus status,
            @RequestParam(required = false) Integer size
    ) {
        List<TeachingMapListResponse> result = teachingMapService.getTeachingMaps(
                getAuthenticatedUserId(authMember),
                status,
                size
        );

        return ResponseEntity.ok(
                ApiResponse.onSuccess(TeachingMapSuccessCode.TEACHING_MAP_LIST_SUCCESS, result)
        );
    }

    private Long getAuthenticatedUserId(AuthMember authMember) {
        if (authMember == null) {
            throw new GeneralException(GlobalErrorCode.UNAUTHORIZED);
        }

        return authMember.getUserId();
    }
}
