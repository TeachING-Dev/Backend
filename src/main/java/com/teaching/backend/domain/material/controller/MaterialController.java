package com.teaching.backend.domain.material.controller;

import com.teaching.backend.domain.material.dto.MaterialIndexResponse;
import com.teaching.backend.domain.material.service.MaterialIndexingService;
import com.teaching.backend.global.response.ApiResponse;
import com.teaching.backend.global.security.entity.AuthMember;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/materials")
public class MaterialController {

    private final MaterialIndexingService materialIndexingService;

    // TODO: 자료 분석 완료 이벤트/파이프라인이 생기면 자동 트리거로 대체하고 이 수동 엔드포인트는 제거
    @PostMapping("/{materialId}/index")
    public ApiResponse<MaterialIndexResponse> index(
            @AuthenticationPrincipal AuthMember authMember,
            @PathVariable Long materialId
    ) {
        int chunkCount = materialIndexingService.indexMaterial(materialId, authMember.getUserId());
        return ApiResponse.onSuccess(new MaterialIndexResponse(materialId, chunkCount));
    }
}
