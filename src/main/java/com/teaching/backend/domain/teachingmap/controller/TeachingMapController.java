package com.teaching.backend.domain.teachingmap.controller;

import com.teaching.backend.domain.teachingmap.code.TeachingMapSuccessCode;
import com.teaching.backend.domain.teachingmap.dto.request.TeachingMapCreateRequest;
import com.teaching.backend.domain.teachingmap.dto.response.TeachingMapCreateResponse;
import com.teaching.backend.domain.teachingmap.dto.response.TeachingMapListResponse;
import com.teaching.backend.domain.teachingmap.enums.TeachingMapStatus;
import com.teaching.backend.domain.teachingmap.service.TeachingMapService;
import com.teaching.backend.global.apiPayload.code.GlobalErrorCode;
import com.teaching.backend.global.exception.GeneralException;
import com.teaching.backend.global.response.ApiResponse;
import com.teaching.backend.global.security.entity.AuthMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@Tag(name = "TeachingMap", description = "티칭맵 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/teaching-maps")
public class TeachingMapController {

    private final TeachingMapService teachingMapService;

    @Operation(
            summary = "학습중인 티칭맵 전체 목록 조회",
            description = "학습중인 티칭맵 전체 목록을 조회합니다."
    )
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

    // 티칭맵 생성
    @Operation(
            summary = "티칭맵 생성",
            description = "사용자의 폴더를 바탕으로 티칭맵을 생성합니다."
    )
    @PostMapping
    public ApiResponse<TeachingMapCreateResponse> createTeachingMap(@AuthenticationPrincipal AuthMember authmember,@Valid @RequestBody TeachingMapCreateRequest request) {

        TeachingMapCreateResponse response =teachingMapService.createTeachingMap(authmember.getUserId(),request);
        return ApiResponse.onSuccess(TeachingMapSuccessCode.TEACHING_MAP_CREATE_SUCCESS, response);


    }
}
