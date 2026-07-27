package com.teaching.backend.domain.teachingmap.repository;

import com.teaching.backend.domain.teachingmap.entity.AiGuide;
import com.teaching.backend.domain.teachingmap.enums.GuideType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiGuideRepository extends JpaRepository<AiGuide, Long> {
    Optional<AiGuide> findByMaterialHighlightIdAndGuideType(Long materialHighlightId, GuideType guideType);
    List<AiGuide> findAllByMaterialHighlightIdInAndGuideType(List<Long> materialHighlightIds, GuideType guideType);
}