package com.teaching.backend.domain.material.service;

import com.teaching.backend.domain.folder.exception.FolderErrorCode;
import com.teaching.backend.domain.folder.exception.FolderException;
import com.teaching.backend.domain.folder.service.FolderService;
import com.teaching.backend.domain.material.dto.request.MaterialAnalyzeRequest;
import com.teaching.backend.domain.material.dto.response.MaterialAnalyzeResponse;
import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.enums.AiStatus;
import com.teaching.backend.domain.material.enums.PlatformType;
import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import com.teaching.backend.domain.material.repository.MaterialRepository;
import com.teaching.backend.global.apiPayload.code.GlobalErrorCode;
import com.teaching.backend.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MaterialUrlAnalysisService {

    private final FolderService folderService;
    private final MaterialRepository materialRepository;
    private final MaterialUrlValidator materialUrlValidator;
    private final MaterialPlatformResolver materialPlatformResolver;

    public MaterialAnalyzeResponse analyze(
            Long userId,
            MaterialAnalyzeRequest request
    ) {
        String originalUrl = validateAndNormalizeUrl(request);
        Long folderId = validateFolderId(request);

        folderService.getOwnedFolder(userId, folderId);
        PlatformType platformType = materialPlatformResolver.resolve(null, originalUrl);

        Optional<Material> completedMaterial = findLatestCompletedMaterial(userId, originalUrl);
        if (completedMaterial.isPresent() && !request.isForceAnalyze()) {
            return MaterialAnalyzeResponse.alreadyAnalyzed(completedMaterial.get());
        }

        return prepareAnalysis(originalUrl, platformType);
    }

    private String validateAndNormalizeUrl(MaterialAnalyzeRequest request) {
        if (request == null || request.url() == null || request.url().isBlank()) {
            throw new MaterialException(MaterialErrorCode.ORIGINAL_URL_REQUIRED);
        }

        String originalUrl = request.url().trim();
        if (!materialUrlValidator.isValidHttpUrl(originalUrl)) {
            throw new GeneralException(GlobalErrorCode.BAD_REQUEST);
        }

        return originalUrl;
    }

    private Long validateFolderId(MaterialAnalyzeRequest request) {
        Long folderId = request == null ? null : request.folderId();
        if (folderId == null || folderId <= 0) {
            throw new FolderException(FolderErrorCode.INVALID_FOLDER_ID);
        }

        return folderId;
    }

    private Optional<Material> findLatestCompletedMaterial(
            Long userId,
            String originalUrl
    ) {
        List<Material> materials = materialRepository.findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(
                userId,
                originalUrl
        );

        return materials.stream()
                .filter(material -> material.getAiStatus() == AiStatus.COMPLETED)
                .max(Comparator
                        .comparing(Material::getCreatedAt)
                        .thenComparing(Material::getId));
    }

    private MaterialAnalyzeResponse prepareAnalysis(
            String originalUrl,
            PlatformType platformType
    ) {
        return MaterialAnalyzeResponse.analysisRequired(originalUrl, platformType);
    }
}
