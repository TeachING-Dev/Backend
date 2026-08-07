package com.teaching.backend.domain.teachingmap.service;

import com.teaching.backend.domain.material.entity.MaterialHighlight;
import com.teaching.backend.domain.material.repository.MaterialAnalysisRepository;
import com.teaching.backend.domain.material.repository.MaterialHighlightRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MaterialHighlightGenerationService {

    private final MaterialAnalysisRepository materialAnalysisRepository;
    private final MaterialHighlightRepository materialHighlightRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryClaim(Long materialAnalysisId) {
        return materialAnalysisRepository.claimHighlightGeneration(materialAnalysisId) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void commit(Long materialAnalysisId, List<MaterialHighlight> highlights) {
        materialHighlightRepository.saveAll(highlights);
        materialAnalysisRepository.completeHighlightGeneration(materialAnalysisId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rollbackClaim(Long materialAnalysisId) {
        materialAnalysisRepository.resetHighlightStatus(materialAnalysisId);
    }
}