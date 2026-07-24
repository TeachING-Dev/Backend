package com.teaching.backend.domain.trash.controller;

import com.teaching.backend.domain.material.code.MaterialSuccessCode;
import com.teaching.backend.domain.material.dto.MaterialRestoreResponse;
import com.teaching.backend.domain.teachingmap.code.TeachingMapSuccessCode;
import com.teaching.backend.domain.teachingmap.dto.response.TeachingMapRestoreResponse;
import com.teaching.backend.domain.trash.code.TrashSuccessCode;
import com.teaching.backend.domain.trash.dto.response.TrashFolderListResponse;
import com.teaching.backend.domain.trash.dto.response.TrashMaterialListResponse;
import com.teaching.backend.domain.trash.dto.response.TrashTeachingMapListResponse;
import com.teaching.backend.domain.trash.service.TrashService;
import com.teaching.backend.global.response.ApiResponse;
import com.teaching.backend.global.security.entity.AuthMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Trash", description = "휴지통(폴더/자료/티칭맵) 목록 조회 및 복구 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/trash")
public class TrashController {

    private final TrashService trashService;

    @Operation(
            summary = "휴지통 폴더 목록 조회",
            description = "휴지통에 있는 폴더 목록을 정렬 기준(latest/oldest)에 따라 조회합니다."
    )
    @GetMapping("/folders")
    public ApiResponse<List<TrashFolderListResponse>> getTrashedFolders(
            @AuthenticationPrincipal AuthMember authMember,
            @RequestParam(required = false) String sort
    ) {
        List<TrashFolderListResponse> result = trashService.getTrashedFolders(authMember.getUserId(), sort);
        return ApiResponse.onSuccess(TrashSuccessCode.TRASH_FOLDER_LIST_SUCCESS, result);
    }

    @Operation(
            summary = "휴지통 자료 목록 조회",
            description = "휴지통에 있는 지식 자료 목록을 정렬 기준(latest/oldest)에 따라 조회합니다."
    )
    @GetMapping("/materials")
    public ApiResponse<List<TrashMaterialListResponse>> getTrashedMaterials(
            @AuthenticationPrincipal AuthMember authMember,
            @RequestParam(required = false) String sort
    ) {
        List<TrashMaterialListResponse> result = trashService.getTrashedMaterials(authMember.getUserId(), sort);
        return ApiResponse.onSuccess(TrashSuccessCode.TRASH_MATERIAL_LIST_SUCCESS, result);
    }

    @Operation(
            summary = "휴지통 티칭맵 목록 조회",
            description = "휴지통에 있는 티칭맵 목록을 정렬 기준(latest/oldest)에 따라 조회합니다."
    )
    @GetMapping("/teaching-maps")
    public ApiResponse<List<TrashTeachingMapListResponse>> getTrashedTeachingMaps(
            @AuthenticationPrincipal AuthMember authMember,
            @RequestParam(required = false) String sort
    ) {
        List<TrashTeachingMapListResponse> result = trashService.getTrashedTeachingMaps(authMember.getUserId(), sort);
        return ApiResponse.onSuccess(TrashSuccessCode.TRASH_TEACHING_MAP_LIST_SUCCESS, result);
    }

    @Operation(
            summary = "자료 복구",
            description = "휴지통 내 지식 자료를 복구합니다. 소속된 상위 폴더가 휴지통에 있으면 폴더를 먼저 복구해야 합니다."
    )
    @PatchMapping("/materials/{materialId}/restore")
    public ApiResponse<MaterialRestoreResponse> restoreMaterial(
            @AuthenticationPrincipal AuthMember authMember,
            @PathVariable Long materialId
    ) {
        MaterialRestoreResponse result = trashService.restoreMaterial(authMember.getUserId(), materialId);
        return ApiResponse.onSuccess(MaterialSuccessCode.MATERIAL_TRASH_RESTORE_SUCCESS, result);
    }

    @Operation(
            summary = "티칭맵 복구",
            description = "휴지통 내 티칭맵을 복구합니다."
    )
    @PatchMapping("/teaching-maps/{mapId}/restore")
    public ApiResponse<TeachingMapRestoreResponse> restoreTeachingMap(
            @AuthenticationPrincipal AuthMember authMember,
            @PathVariable Long mapId
    ) {
        TeachingMapRestoreResponse result = trashService.restoreTeachingMap(authMember.getUserId(), mapId);
        return ApiResponse.onSuccess(TeachingMapSuccessCode.TEACHING_MAP_RESTORE_SUCCESS, result);
    }
}
