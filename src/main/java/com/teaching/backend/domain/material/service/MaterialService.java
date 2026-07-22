package com.teaching.backend.domain.material.service;

import com.teaching.backend.domain.material.dto.MaterialListResponse;
import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.entity.MaterialAnalysis;
import com.teaching.backend.domain.material.repository.MaterialAnalysisRepository;
import com.teaching.backend.domain.material.repository.MaterialRepository;
import com.teaching.backend.domain.tag.entity.MaterialTag;
import com.teaching.backend.domain.tag.exception.TagErrorCode;
import com.teaching.backend.domain.tag.exception.TagException;
import com.teaching.backend.domain.tag.repository.MaterialTagRepository;
import com.teaching.backend.global.apiPayload.code.GlobalErrorCode;
import com.teaching.backend.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MaterialService {

    private final MaterialRepository materialRepository;
    private final MaterialAnalysisRepository materialAnalysisRepository;
    private final MaterialTagRepository materialTagRepository;

    public List<MaterialListResponse> getMaterialList(Long userId, Integer size) {
        List<Material> materials = findRecentMaterials(userId, size);
        if (materials.isEmpty()) {
            return List.of();
        }

        List<Long> materialIds = materials.stream()
                .map(Material::getId)
                .toList();

        Map<Long, String> summaryByMaterialId = materialAnalysisRepository.findAllActiveByMaterialIds(materialIds)
                .stream()
                .collect(Collectors.toMap(
                        analysis -> analysis.getMaterial().getId(),
                        MaterialAnalysis::getSummary,
                        (current, ignored) -> current
                ));

        return materials.stream()
                .map(material -> MaterialListResponse.of(
                        material,
                        summaryByMaterialId.get(material.getId())
                ))
                .toList();
    }

    @Transactional
    public void deleteMaterialTag(Long userId, Long materialTagId) {
        validateMaterialTagId(materialTagId);

        MaterialTag materialTag = materialTagRepository.findByIdWithMaterialAndUser(materialTagId)
                .orElseThrow(() -> new TagException(TagErrorCode.TAG_NOT_FOUND));

        if (!materialTag.getMaterial().getUser().getId().equals(userId)) {
            throw new TagException(TagErrorCode.TAG_ACCESS_DENIED);
        }

        materialTagRepository.delete(materialTag);
    }

    private List<Material> findRecentMaterials(Long userId, Integer size) {
        Sort recentSort = Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")
        );

        if (size == null) {
            return materialRepository.findAllByUser_Id(userId, recentSort);
        }

        if (size <= 0) {
            throw new GeneralException(GlobalErrorCode.BAD_REQUEST);
        }

        return materialRepository.findAllByUser_Id(userId, PageRequest.of(0, size, recentSort))
                .getContent();
    }

    private void validateMaterialTagId(Long materialTagId) {
        if (materialTagId == null || materialTagId <= 0) {
            throw new TagException(TagErrorCode.TAG_NOT_FOUND);
        }
    }
}
