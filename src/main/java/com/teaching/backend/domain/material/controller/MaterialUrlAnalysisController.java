package com.teaching.backend.domain.material.controller;

import com.teaching.backend.domain.material.code.MaterialSuccessCode;
import com.teaching.backend.domain.material.dto.request.MaterialAnalyzeRequest;
import com.teaching.backend.domain.material.dto.response.MaterialAnalyzeResponse;
import com.teaching.backend.domain.material.service.MaterialUrlAnalysisService;
import com.teaching.backend.global.apiPayload.code.GlobalErrorCode;
import com.teaching.backend.global.exception.GeneralException;
import com.teaching.backend.global.response.ApiResponse;
import com.teaching.backend.global.security.entity.AuthMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Material", description = "자료 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/materials")
public class MaterialUrlAnalysisController {

    private final MaterialUrlAnalysisService materialUrlAnalysisService;

    @Operation(
            summary = "URL 기반 AI 분석 요청",
            description = "URL과 저장 폴더를 검증하고, 동일 URL 분석 결과가 있는지 확인합니다."
    )
    @PostMapping("/analyze")
    public ResponseEntity<ApiResponse<MaterialAnalyzeResponse>> analyze(
            @Parameter(hidden = true)
            @AuthenticationPrincipal AuthMember authMember,

            @RequestBody MaterialAnalyzeRequest request
    ) {
        MaterialAnalyzeResponse result = materialUrlAnalysisService.analyze(
                getAuthenticatedUserId(authMember),
                request
        );

        return ResponseEntity.ok(
                ApiResponse.onSuccess(MaterialSuccessCode.MATERIAL_URL_ANALYSIS_READY, result)
        );
    }

    private Long getAuthenticatedUserId(AuthMember authMember) {
        if (authMember == null) {
            throw new GeneralException(GlobalErrorCode.UNAUTHORIZED);
        }

        return authMember.getUserId();
    }
}
