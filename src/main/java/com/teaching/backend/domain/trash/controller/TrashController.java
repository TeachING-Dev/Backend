package com.teaching.backend.domain.trash.controller;

import com.teaching.backend.domain.folder.code.FolderSuccessCode;
import com.teaching.backend.domain.folder.dto.request.FolderIdsRequest;
import com.teaching.backend.domain.folder.dto.response.FolderTrashRestoreResponse;
import com.teaching.backend.domain.material.code.MaterialSuccessCode;
import com.teaching.backend.domain.material.dto.MaterialRestoreResponse;
import com.teaching.backend.domain.material.dto.request.MaterialIdsRequest;
import com.teaching.backend.domain.teachingmap.code.TeachingMapSuccessCode;
import com.teaching.backend.domain.teachingmap.dto.request.TeachingMapIdsRequest;
import com.teaching.backend.domain.teachingmap.dto.response.TeachingMapRestoreResponse;
import com.teaching.backend.domain.trash.code.TrashSuccessCode;
import com.teaching.backend.domain.trash.dto.response.TrashFolderListResponse;
import com.teaching.backend.domain.trash.dto.response.TrashFolderMaterialListResponse;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Trash", description = "휴지통(폴더/자료/티칭맵) 목록 조회 및 복구 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/trash")
public class TrashController {

    private final TrashService trashService;

    @Operation(
            summary = "휴지통 폴더 목록 조회",
            description = "휴지통에 있는 폴더 목록을 정렬 기준(latest/oldest)에 따라 9개씩 페이지네이션하여 조회합니다."
    )
    @GetMapping("/folders")
    public ApiResponse<TrashFolderListResponse> getTrashedFolders(
            @AuthenticationPrincipal AuthMember authMember,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer page
    ) {
        TrashFolderListResponse result = trashService.getTrashedFolders(authMember.getUserId(), sort, page);
        return ApiResponse.onSuccess(TrashSuccessCode.TRASH_FOLDER_LIST_SUCCESS, result);
    }

    @Operation(
            summary = "휴지통 자료 목록 조회",
            description = "휴지통에 있는 지식 자료 목록을 정렬 기준(latest/oldest)에 따라 10개씩 페이지네이션하여 조회합니다."
    )
    @GetMapping("/materials")
    public ApiResponse<TrashMaterialListResponse> getTrashedMaterials(
            @AuthenticationPrincipal AuthMember authMember,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer page
    ) {
        TrashMaterialListResponse result = trashService.getTrashedMaterials(authMember.getUserId(), sort, page);
        return ApiResponse.onSuccess(TrashSuccessCode.TRASH_MATERIAL_LIST_SUCCESS, result);
    }

    @Operation(
            summary = "휴지통 폴더 상세(내부 자료) 조회",
            description = "휴지통에 있는 폴더 안에 함께 삭제됐던 자료 목록을 정렬 기준(latest/oldest)에 따라 10개씩 페이지네이션하여 조회합니다."
    )
    @GetMapping("/folders/{folderId}/materials")
    public ApiResponse<TrashFolderMaterialListResponse> getTrashedFolderMaterials(
            @AuthenticationPrincipal AuthMember authMember,
            @PathVariable Long folderId,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer page
    ) {
        TrashFolderMaterialListResponse result = trashService.getTrashedFolderMaterials(authMember.getUserId(), folderId, sort, page);
        return ApiResponse.onSuccess(TrashSuccessCode.TRASH_FOLDER_MATERIAL_LIST_SUCCESS, result);
    }

    @Operation(
            summary = "휴지통 티칭맵 목록 조회",
            description = "휴지통에 있는 티칭맵 목록을 정렬 기준(latest/oldest)에 따라 10개씩 페이지네이션하여 조회합니다."
    )
    @GetMapping("/teaching-maps")
    public ApiResponse<TrashTeachingMapListResponse> getTrashedTeachingMaps(
            @AuthenticationPrincipal AuthMember authMember,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer page
    ) {
        TrashTeachingMapListResponse result = trashService.getTrashedTeachingMaps(authMember.getUserId(), sort, page);
        return ApiResponse.onSuccess(TrashSuccessCode.TRASH_TEACHING_MAP_LIST_SUCCESS, result);
    }

    @Operation(
            summary = "폴더 다중 복구",
            description = "휴지통 내 폴더를 여러 개 선택하여 한 번에 복구합니다. 활성 폴더와 이름이 겹치는 폴더는 복구되지 않고 failedIds로 반환됩니다."
    )
    @PatchMapping("/folders/restore")
    public ApiResponse<FolderTrashRestoreResponse> restoreFolders(
            @AuthenticationPrincipal AuthMember authMember,
            @RequestBody FolderIdsRequest request
    ) {
        FolderTrashRestoreResponse result = trashService.restoreFolders(authMember.getUserId(), request);
        return ApiResponse.onSuccess(FolderSuccessCode.FOLDER_TRASH_RESTORE_SUCCESS, result);
    }

    @Operation(
            summary = "자료 다중 복구",
            description = "휴지통 내 지식 자료를 여러 개 선택하여 한 번에 복구합니다. 상위 폴더가 휴지통에 있는 자료는 복구되지 않고 failedIds로 반환됩니다."
    )
    @PatchMapping("/materials/restore")
    public ApiResponse<MaterialRestoreResponse> restoreMaterials(
            @AuthenticationPrincipal AuthMember authMember,
            @RequestBody MaterialIdsRequest request
    ) {
        MaterialRestoreResponse result = trashService.restoreMaterials(authMember.getUserId(), request);
        return ApiResponse.onSuccess(MaterialSuccessCode.MATERIAL_TRASH_RESTORE_SUCCESS, result);
    }

    @Operation(
            summary = "티칭맵 다중 복구",
            description = "휴지통 내 티칭맵을 여러 개 선택하여 한 번에 복구합니다."
    )
    @PatchMapping("/teaching-maps/restore")
    public ApiResponse<TeachingMapRestoreResponse> restoreTeachingMaps(
            @AuthenticationPrincipal AuthMember authMember,
            @RequestBody TeachingMapIdsRequest request
    ) {
        TeachingMapRestoreResponse result = trashService.restoreTeachingMaps(authMember.getUserId(), request);
        return ApiResponse.onSuccess(TeachingMapSuccessCode.TEACHING_MAP_RESTORE_SUCCESS, result);
    }
}
