package com.teaching.backend.domain.material.controller;

import com.teaching.backend.domain.material.dto.MaterialIndexResponse;
import com.teaching.backend.domain.material.service.MaterialIndexingService;
import com.teaching.backend.global.response.ApiResponse;
import com.teaching.backend.global.security.entity.AuthMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Material", description = "자료 색인(청킹/임베딩/Qdrant 저장) API - 자료 분석 완료 파이프라인이 생기기 전까지 쓰는 수동 트리거")
@RestController
@RequiredArgsConstructor
@RequestMapping("/materials")
public class MaterialController {

    private final MaterialIndexingService materialIndexingService;

    // TODO: 자료 분석 완료 이벤트/파이프라인이 생기면 자동 트리거로 대체하고 이 수동 엔드포인트는 제거
    @Operation(
            summary = "자료 색인 (수동 트리거)",
            description = "자료의 분석 결과(detailAnalysis)를 청킹 -> 임베딩 -> Qdrant 색인합니다. 이미 색인된 자료는 409로 거부됩니다."
    )
    @PostMapping("/{materialId}/index")
    public ApiResponse<MaterialIndexResponse> index(
            @AuthenticationPrincipal AuthMember authMember,
            @PathVariable Long materialId
    ) {
        int chunkCount = materialIndexingService.indexMaterial(materialId, authMember.getUserId());
        return ApiResponse.onSuccess(new MaterialIndexResponse(materialId, chunkCount));
    }
}
