package com.teaching.backend.domain.folder.controller;

import com.teaching.backend.domain.folder.code.FolderSuccessCode;
import com.teaching.backend.domain.folder.dto.request.FolderCreateRequest;
import com.teaching.backend.domain.folder.dto.request.FolderRenameRequest;
import com.teaching.backend.domain.folder.dto.response.FolderCreateResponse;
import com.teaching.backend.domain.folder.dto.response.FolderDetailResponse;
import com.teaching.backend.domain.folder.dto.response.FolderListResponse;
import com.teaching.backend.domain.folder.dto.response.FolderMaterialListResponse;
import com.teaching.backend.domain.folder.dto.response.FolderRestoreResponse;
import com.teaching.backend.domain.folder.dto.response.FolderRenameResponse;
import com.teaching.backend.domain.folder.dto.response.FolderTrashResponse;
import com.teaching.backend.domain.folder.exception.FolderErrorCode;
import com.teaching.backend.domain.folder.exception.FolderException;
import com.teaching.backend.domain.folder.service.FolderService;
import com.teaching.backend.domain.material.code.MaterialSuccessCode;
import com.teaching.backend.domain.material.dto.request.MaterialAnalysisGenerateRequest;
import com.teaching.backend.domain.material.dto.request.MaterialAnalysisSummaryUpdateRequest;
import com.teaching.backend.domain.material.dto.request.MaterialIdsRequest;
import com.teaching.backend.domain.material.dto.request.MaterialMoveRequest;
import com.teaching.backend.domain.material.dto.response.MaterialAnalysisResponse;
import com.teaching.backend.domain.material.dto.response.MaterialAnalysisSummaryUpdateResponse;
import com.teaching.backend.domain.material.dto.response.MaterialDetailResponse;
import com.teaching.backend.domain.material.dto.response.MaterialMoveResponse;
import com.teaching.backend.domain.material.dto.response.MaterialOriginUrlResponse;
import com.teaching.backend.domain.material.dto.response.MaterialRestoreResponse;
import com.teaching.backend.domain.material.dto.response.MaterialTagResponse;
import com.teaching.backend.domain.material.dto.response.MaterialTrashResponse;
import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import com.teaching.backend.domain.material.service.MaterialService;
import com.teaching.backend.global.apiPayload.code.GlobalErrorCode;
import com.teaching.backend.global.exception.GeneralException;
import com.teaching.backend.global.response.ApiResponse;
import com.teaching.backend.global.security.entity.AuthMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Folder", description = "폴더 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/folders")
public class FolderController {

    private final FolderService folderService;
    private final MaterialService materialService;

    @Operation(
            summary = "폴더 목록 조회",
            description = "Authorization Bearer access token으로 인증된 사용자의 폴더 목록을 정렬 기준에 따라 조회합니다."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<List<FolderListResponse>>> getFolderList(
            @Parameter(hidden = true)
            @AuthenticationPrincipal AuthMember authMember,

            @Parameter(
                    description = "정렬 기준입니다. recent, oldest, name 중 하나를 입력합니다.",
                    example = "recent"
            )
            @RequestParam(
                    name = "sort",
                    defaultValue = "recent"
            )
            String sort
    ) {
        List<FolderListResponse> result = folderService.getFolderList(
                getAuthenticatedUserId(authMember),
                sort
        );

        return ResponseEntity.ok(
                ApiResponse.onSuccess(FolderSuccessCode.FOLDER_LIST_SUCCESS, result)
        );
    }

    @Operation(
            summary = "폴더 생성",
            description = "Authorization Bearer access token으로 인증된 사용자가 최대 10자의 폴더명을 입력하여 새 폴더를 생성합니다."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<FolderCreateResponse>> createFolder(
            @Parameter(hidden = true)
            @AuthenticationPrincipal AuthMember authMember,

            @Valid @RequestBody FolderCreateRequest request
    ) {
        FolderCreateResponse result = folderService.createFolder(
                getAuthenticatedUserId(authMember),
                request
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.onSuccess(FolderSuccessCode.FOLDER_CREATE_SUCCESS, result));
    }

    @Operation(
            summary = "폴더 상세 조회",
            description = "Authorization Bearer access token으로 인증된 사용자의 폴더 기본 정보를 조회합니다."
    )
    @GetMapping("/{folderId}")
    public ResponseEntity<ApiResponse<FolderDetailResponse>> getFolderDetail(
            @Parameter(hidden = true)
            @AuthenticationPrincipal AuthMember authMember,

            @Parameter(
                    description = "조회할 폴더 ID",
                    example = "1"
            )
            @PathVariable String folderId
    ) {
        FolderDetailResponse result = folderService.getFolderDetail(
                getAuthenticatedUserId(authMember),
                parseFolderId(folderId)
        );

        return ResponseEntity.ok(
                ApiResponse.onSuccess(FolderSuccessCode.FOLDER_DETAIL_SUCCESS, result)
        );
    }

    @Operation(
            summary = "폴더 내부 자료 목록 조회",
            description = "특정 폴더의 자료를 검색, 정렬 및 페이지 단위로 조회합니다."
    )
    @GetMapping("/{folderId}/materials")
    public ResponseEntity<ApiResponse<FolderMaterialListResponse>> getFolderMaterials(
            @Parameter(hidden = true)
            @AuthenticationPrincipal AuthMember authMember,

            @Parameter(
                    description = "조회할 폴더 ID",
                    example = "1"
            )
            @PathVariable String folderId,

            @Parameter(
                    description = "자료 제목 또는 요약 검색어",
                    example = "node"
            )
            @RequestParam(required = false) String keyword,

            @Parameter(
                    description = "정렬 기준입니다. recent, oldest, title 중 하나를 입력합니다.",
                    example = "recent"
            )
            @RequestParam(
                    name = "sort",
                    defaultValue = "recent"
            )
            String sort,

            @Parameter(
                    description = "0부터 시작하는 페이지 번호",
                    example = "0"
            )
            @RequestParam(required = false) Integer page,

            @Parameter(
                    description = "페이지 크기",
                    example = "10"
            )
            @RequestParam(required = false) Integer size
    ) {
        FolderMaterialListResponse result = folderService.getFolderMaterials(
                getAuthenticatedUserId(authMember),
                parseFolderId(folderId),
                keyword,
                sort,
                page,
                size
        );

        return ResponseEntity.ok(
                ApiResponse.onSuccess(MaterialSuccessCode.FOLDER_MATERIAL_LIST_SUCCESS, result)
        );
    }

    @Operation(
            summary = "폴더명 수정",
            description = "Authorization Bearer access token으로 인증된 사용자의 폴더명을 최대 10자까지 수정합니다."
    )
    @PatchMapping("/{folderId}")
    public ResponseEntity<ApiResponse<FolderRenameResponse>> renameFolder(
            @Parameter(hidden = true)
            @AuthenticationPrincipal AuthMember authMember,

            @Parameter(
                    description = "수정할 폴더 ID",
                    example = "1"
            )
            @PathVariable String folderId,

            @Valid @RequestBody FolderRenameRequest request
    ) {
        FolderRenameResponse result = folderService.renameFolder(
                getAuthenticatedUserId(authMember),
                parseFolderId(folderId),
                request
        );

        return ResponseEntity.ok(
                ApiResponse.onSuccess(FolderSuccessCode.FOLDER_RENAME_SUCCESS, result)
        );
    }

    @Operation(
            summary = "폴더 휴지통 이동",
            description = "Authorization Bearer access token으로 인증된 사용자의 폴더를 휴지통으로 이동합니다."
    )
    @PatchMapping("/{folderId}/trash")
    public ResponseEntity<ApiResponse<FolderTrashResponse>> moveFolderToTrash(
            @Parameter(hidden = true)
            @AuthenticationPrincipal AuthMember authMember,

            @Parameter(
                    description = "휴지통으로 이동할 폴더 ID",
                    example = "1"
            )
            @PathVariable String folderId
    ) {
        FolderTrashResponse result = folderService.moveFolderToTrash(
                getAuthenticatedUserId(authMember),
                parseFolderId(folderId)
        );

        return ResponseEntity.ok(
                ApiResponse.onSuccess(FolderSuccessCode.FOLDER_TRASH_SUCCESS, result)
        );
    }

    @Operation(
            summary = "폴더 복구",
            description = "Authorization Bearer access token으로 인증된 사용자의 휴지통 폴더를 복구합니다."
    )
    @PatchMapping("/{folderId}/restore")
    public ResponseEntity<ApiResponse<FolderRestoreResponse>> restoreFolder(
            @Parameter(hidden = true)
            @AuthenticationPrincipal AuthMember authMember,

            @Parameter(
                    description = "복구할 폴더 ID",
                    example = "1"
            )
            @PathVariable String folderId
    ) {
        FolderRestoreResponse result = folderService.restoreFolder(
                getAuthenticatedUserId(authMember),
                parseFolderId(folderId)
        );

        return ResponseEntity.ok(
                ApiResponse.onSuccess(FolderSuccessCode.FOLDER_RESTORE_SUCCESS, result)
        );
    }

    @Operation(
            summary = "자료 생성 + AI 분석",
            description = "URL/본문 텍스트를 받아 해당 폴더에 자료를 생성하고, AI 요약/상세 분석/태그를 생성합니다."
    )
    @PostMapping("/{folderId}/materials/analyze")
    public ResponseEntity<ApiResponse<MaterialAnalysisResponse>> generateMaterialWithAnalysis(
            @Parameter(hidden = true)
            @AuthenticationPrincipal AuthMember authMember,

            @Parameter(description = "저장할 폴더 ID", example = "1")
            @PathVariable String folderId,

            @RequestBody MaterialAnalysisGenerateRequest request
    ) {
        MaterialAnalysisResponse result = materialService.generateMaterialWithAnalysis(
                getAuthenticatedUserId(authMember),
                parseFolderId(folderId),
                request
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.onSuccess(MaterialSuccessCode.MATERIAL_ANALYSIS_GENERATE_SUCCESS, result));
    }

    @Operation(
            summary = "자료 상세 조회",
            description = "자료 상세 화면에 필요한 기본 정보를 조회합니다."
    )
    @GetMapping("/{folderId}/materials/{materialId}")
    public ResponseEntity<ApiResponse<MaterialDetailResponse>> getMaterialDetail(
            @Parameter(hidden = true)
            @AuthenticationPrincipal AuthMember authMember,

            @Parameter(description = "폴더 ID", example = "1")
            @PathVariable String folderId,

            @Parameter(description = "자료 ID", example = "101")
            @PathVariable String materialId
    ) {
        MaterialDetailResponse result = materialService.getMaterialDetail(
                getAuthenticatedUserId(authMember),
                parseFolderId(folderId),
                parseMaterialId(materialId)
        );

        return ResponseEntity.ok(
                ApiResponse.onSuccess(MaterialSuccessCode.MATERIAL_DETAIL_SUCCESS, result)
        );
    }

    @Operation(
            summary = "AI 상세 분석 조회",
            description = "자료 상세 화면에서 AI 요약과 AI 상세 분석 내용을 조회합니다."
    )
    @GetMapping("/{folderId}/materials/{materialId}/analysis")
    public ResponseEntity<ApiResponse<MaterialAnalysisResponse>> getMaterialAnalysis(
            @Parameter(hidden = true)
            @AuthenticationPrincipal AuthMember authMember,

            @Parameter(description = "폴더 ID", example = "1")
            @PathVariable String folderId,

            @Parameter(description = "자료 ID", example = "101")
            @PathVariable String materialId
    ) {
        MaterialAnalysisResponse result = materialService.getMaterialAnalysis(
                getAuthenticatedUserId(authMember),
                parseFolderId(folderId),
                parseMaterialId(materialId)
        );

        return ResponseEntity.ok(
                ApiResponse.onSuccess(MaterialSuccessCode.MATERIAL_ANALYSIS_SUCCESS, result)
        );
    }

    @Operation(
            summary = "AI 요약 수정",
            description = "사용자가 AI 요약 내용을 직접 수정합니다."
    )
    @PatchMapping("/{folderId}/materials/{materialId}/analysis/summary")
    public ResponseEntity<ApiResponse<MaterialAnalysisSummaryUpdateResponse>> updateMaterialAnalysisSummary(
            @Parameter(hidden = true)
            @AuthenticationPrincipal AuthMember authMember,

            @Parameter(description = "폴더 ID", example = "1")
            @PathVariable String folderId,

            @Parameter(description = "자료 ID", example = "101")
            @PathVariable String materialId,

            @RequestBody MaterialAnalysisSummaryUpdateRequest request
    ) {
        MaterialAnalysisSummaryUpdateResponse result = materialService.updateAnalysisSummary(
                getAuthenticatedUserId(authMember),
                parseFolderId(folderId),
                parseMaterialId(materialId),
                request
        );

        return ResponseEntity.ok(
                ApiResponse.onSuccess(MaterialSuccessCode.MATERIAL_ANALYSIS_SUMMARY_UPDATE_SUCCESS, result)
        );
    }

    @Operation(
            summary = "태그 조회",
            description = "자료에 연결된 태그 목록을 조회합니다."
    )
    @GetMapping("/{folderId}/materials/{materialId}/tags")
    public ResponseEntity<ApiResponse<List<MaterialTagResponse>>> getMaterialTags(
            @Parameter(hidden = true)
            @AuthenticationPrincipal AuthMember authMember,

            @Parameter(description = "폴더 ID", example = "1")
            @PathVariable String folderId,

            @Parameter(description = "자료 ID", example = "101")
            @PathVariable String materialId
    ) {
        List<MaterialTagResponse> result = materialService.getMaterialTags(
                getAuthenticatedUserId(authMember),
                parseFolderId(folderId),
                parseMaterialId(materialId)
        );

        return ResponseEntity.ok(
                ApiResponse.onSuccess(MaterialSuccessCode.MATERIAL_TAG_LIST_SUCCESS, result)
        );
    }

    @Operation(
            summary = "원본 URL 조회",
            description = "자료의 원문 URL을 조회합니다."
    )
    @GetMapping("/{folderId}/materials/{materialId}/origin-url")
    public ResponseEntity<ApiResponse<MaterialOriginUrlResponse>> getMaterialOriginUrl(
            @Parameter(hidden = true)
            @AuthenticationPrincipal AuthMember authMember,

            @Parameter(description = "폴더 ID", example = "1")
            @PathVariable String folderId,

            @Parameter(description = "자료 ID", example = "101")
            @PathVariable String materialId
    ) {
        MaterialOriginUrlResponse result = materialService.getMaterialOriginUrl(
                getAuthenticatedUserId(authMember),
                parseFolderId(folderId),
                parseMaterialId(materialId)
        );

        return ResponseEntity.ok(
                ApiResponse.onSuccess(MaterialSuccessCode.MATERIAL_ORIGIN_URL_SUCCESS, result)
        );
    }

    @Operation(
            summary = "자료 이동",
            description = "현재 폴더에 있는 선택 자료를 다른 폴더로 이동합니다."
    )
    @PatchMapping("/{folderId}/materials/move")
    public ResponseEntity<ApiResponse<MaterialMoveResponse>> moveMaterials(
            @Parameter(hidden = true)
            @AuthenticationPrincipal AuthMember authMember,

            @Parameter(description = "현재 폴더 ID", example = "1")
            @PathVariable String folderId,

            @RequestBody MaterialMoveRequest request
    ) {
        MaterialMoveResponse result = materialService.moveMaterials(
                getAuthenticatedUserId(authMember),
                parseFolderId(folderId),
                request
        );

        return ResponseEntity.ok(
                ApiResponse.onSuccess(MaterialSuccessCode.MATERIAL_MOVE_SUCCESS, result)
        );
    }

    @Operation(
            summary = "자료 휴지통 이동",
            description = "현재 폴더에 있는 선택 자료를 휴지통으로 이동합니다."
    )
    @PatchMapping("/{folderId}/materials/trash")
    public ResponseEntity<ApiResponse<MaterialTrashResponse>> trashMaterials(
            @Parameter(hidden = true)
            @AuthenticationPrincipal AuthMember authMember,

            @Parameter(description = "현재 폴더 ID", example = "1")
            @PathVariable String folderId,

            @RequestBody MaterialIdsRequest request
    ) {
        MaterialTrashResponse result = materialService.trashMaterials(
                getAuthenticatedUserId(authMember),
                parseFolderId(folderId),
                request
        );

        return ResponseEntity.ok(
                ApiResponse.onSuccess(MaterialSuccessCode.MATERIAL_TRASH_SUCCESS, result)
        );
    }

    @Operation(
            summary = "자료 복구",
            description = "휴지통에 있는 자료를 특정 폴더로 복구합니다."
    )
    @PatchMapping("/{folderId}/materials/restore")
    public ResponseEntity<ApiResponse<MaterialRestoreResponse>> restoreMaterials(
            @Parameter(hidden = true)
            @AuthenticationPrincipal AuthMember authMember,

            @Parameter(description = "복구될 폴더 ID", example = "1")
            @PathVariable String folderId,

            @RequestBody MaterialIdsRequest request
    ) {
        MaterialRestoreResponse result = materialService.restoreMaterials(
                getAuthenticatedUserId(authMember),
                parseFolderId(folderId),
                request
        );

        return ResponseEntity.ok(
                ApiResponse.onSuccess(MaterialSuccessCode.MATERIAL_RESTORE_SUCCESS, result)
        );
    }

    private Long getAuthenticatedUserId(AuthMember authMember) {
        if (authMember == null) {
            throw new GeneralException(GlobalErrorCode.UNAUTHORIZED);
        }

        return authMember.getUserId();
    }

    private Long parseFolderId(String folderId) {
        if (folderId == null || folderId.isBlank()) {
            throw new FolderException(FolderErrorCode.INVALID_FOLDER_ID);
        }

        try {
            return Long.parseLong(folderId);
        } catch (NumberFormatException e) {
            throw new FolderException(FolderErrorCode.INVALID_FOLDER_ID);
        }
    }

    private Long parseMaterialId(String materialId) {
        if (materialId == null || materialId.isBlank()) {
            throw new MaterialException(MaterialErrorCode.INVALID_MATERIAL_ID);
        }

        try {
            return Long.parseLong(materialId);
        } catch (NumberFormatException e) {
            throw new MaterialException(MaterialErrorCode.INVALID_MATERIAL_ID);
        }
    }
}
