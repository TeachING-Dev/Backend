package com.teaching.backend.domain.teachingmap.controller;

import com.teaching.backend.domain.teachingmap.code.TeachingMapSuccessCode;
import com.teaching.backend.domain.teachingmap.dto.request.TeachingMapCreateRequest;
import com.teaching.backend.domain.teachingmap.dto.response.HighlightAnalysisResponse;
import com.teaching.backend.domain.teachingmap.dto.response.TeachingMapCreateResponse;
import com.teaching.backend.domain.teachingmap.dto.response.TeachingMapListResponse;
import com.teaching.backend.domain.teachingmap.dto.response.TeachingMapStepDetailResponse;
import com.teaching.backend.domain.teachingmap.enums.GuideType;
import com.teaching.backend.domain.teachingmap.enums.TeachingMapListSort;
import com.teaching.backend.domain.teachingmap.enums.TeachingMapStatus;
import com.teaching.backend.domain.teachingmap.enums.TeachingMapType;
import com.teaching.backend.domain.teachingmap.service.HighlightAnalysisService;
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
    private final HighlightAnalysisService highlightAnalysisService;

    @Operation(
            summary = "티칭맵 전체 목록 조회",
            description = "티칭맵 전체 목록을 조회합니다."
    )
    @GetMapping
    public ApiResponse<TeachingMapListResponse> getTeachingMaps(
            @AuthenticationPrincipal AuthMember authMember,
            @RequestParam(defaultValue = "IN_PROGRESS") TeachingMapStatus status,
            @RequestParam(defaultValue = "ALL") TeachingMapType type,
            @RequestParam(defaultValue = "LATEST") TeachingMapListSort sort
    ) {
        TeachingMapListResponse result = teachingMapService.getTeachingMaps(
                getAuthenticatedUserId(authMember),
                status,
                type,
                sort
        );
        return ApiResponse.onSuccess(TeachingMapSuccessCode.TEACHING_MAP_LIST_SUCCESS, result);
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
    public ApiResponse<TeachingMapCreateResponse> createTeachingMap(
            @AuthenticationPrincipal AuthMember authMember,
            @Valid @RequestBody TeachingMapCreateRequest request
    ) {
        Long userId = getAuthenticatedUserId(authMember);
        TeachingMapCreateResponse response =
                teachingMapService.createTeachingMap(userId, request);
        return ApiResponse.onSuccess(TeachingMapSuccessCode.TEACHING_MAP_CREATE_SUCCESS, response);
    }


    @Operation(
            summary = "티칭맵 스텝 상세 조회",
            description = "티칭맵의 특정 스텝을 조회합니다. 자료 AI 분석 결과와 하이라이트, AI 선생님 피드백을 함께 반환합니다."
    )
    @GetMapping("/{teachingMapId}/steps/{stepId}")
    public ApiResponse<TeachingMapStepDetailResponse> getStepDetail(
            @AuthenticationPrincipal AuthMember authMember,
            @PathVariable Long teachingMapId,
            @PathVariable Long stepId
    ) {
        TeachingMapStepDetailResponse response = teachingMapService.getStepDetail(
                getAuthenticatedUserId(authMember), teachingMapId, stepId
        );
        return ApiResponse.onSuccess(TeachingMapSuccessCode.TEACHING_MAP_STEP_DETAIL_SUCCESS, response);
    }

    @Operation(
            summary = "하이라이트 AI 선생님 분석 조회",
            description = "사용자가 클릭한 하이라이트에 대한 AI 선생님 해설(타카의 분석)을 조회하거나, 없으면 생성합니다. "
                    + "AI 선생님의 말투(guideType)는 사용자의 현재 설정된 teacherPersona 기준으로 자동 결정되며, 별도로 지정할 수 없습니다."
    )
    @GetMapping("/materials/{materialId}/highlights/{highlightId}/analysis")
    public ApiResponse<HighlightAnalysisResponse> getHighlightAnalysis(
            @AuthenticationPrincipal AuthMember authMember,
            @PathVariable Long materialId,
            @PathVariable Long highlightId
    ) {
        HighlightAnalysisResponse response = highlightAnalysisService.getOrGenerateAiGuide(
                getAuthenticatedUserId(authMember), materialId, highlightId
        );
        return ApiResponse.onSuccess(TeachingMapSuccessCode.HIGHLIGHT_ANALYSIS_SUCCESS, response);
    }

}
