package com.teaching.backend.domain.material.controller;

import com.teaching.backend.domain.material.code.MaterialSuccessCode;
import com.teaching.backend.domain.material.dto.MaterialIndexResponse;
import com.teaching.backend.domain.material.dto.MaterialListResponse;
import com.teaching.backend.domain.material.service.MaterialIndexingService;
import com.teaching.backend.domain.material.service.MaterialService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Material", description = "자료 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/materials")
public class MaterialController {

    private final MaterialIndexingService materialIndexingService;
    private final MaterialService materialService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<MaterialListResponse>>> getMaterials(
            @AuthenticationPrincipal AuthMember authMember,
            @RequestParam(required = false) Integer size
    ) {
        List<MaterialListResponse> result = materialService.getMaterialList(
                getAuthenticatedUserId(authMember),
                size
        );

        return ResponseEntity.ok(
                ApiResponse.onSuccess(MaterialSuccessCode.MATERIAL_LIST_SUCCESS, result)
        );
    }

    @Operation(
            summary = "자료 색인",
            description = "자료의 분석 결과를 chunk로 나누고 Qdrant에 색인합니다."
    )
    @PostMapping("/{materialId}/index")
    public ApiResponse<MaterialIndexResponse> index(
            @AuthenticationPrincipal AuthMember authMember,
            @PathVariable Long materialId
    ) {
        int chunkCount = materialIndexingService.indexMaterial(materialId, authMember.getUserId());
        return ApiResponse.onSuccess(new MaterialIndexResponse(materialId, chunkCount));
    }

    private Long getAuthenticatedUserId(AuthMember authMember) {
        if (authMember == null) {
            throw new GeneralException(GlobalErrorCode.UNAUTHORIZED);
        }

        return authMember.getUserId();
    }
}
