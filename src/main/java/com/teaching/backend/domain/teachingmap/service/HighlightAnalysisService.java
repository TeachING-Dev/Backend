package com.teaching.backend.domain.teachingmap.service;

import com.teaching.backend.domain.material.entity.MaterialAnalysis;
import com.teaching.backend.domain.material.entity.MaterialHighlight;
import com.teaching.backend.domain.material.enums.HighlightType;
import com.teaching.backend.domain.material.repository.MaterialAnalysisRepository;
import com.teaching.backend.domain.material.repository.MaterialHighlightRepository;
import com.teaching.backend.domain.teachingmap.dto.response.HighlightAnalysisResponse;
import com.teaching.backend.domain.teachingmap.entity.AiGuide;
import com.teaching.backend.domain.teachingmap.enums.AiGuideContentType;
import com.teaching.backend.domain.teachingmap.enums.GuideType;
import com.teaching.backend.domain.teachingmap.exception.TeachingMapErrorCode;
import com.teaching.backend.domain.teachingmap.repository.AiGuideRepository;
import com.teaching.backend.domain.user.entity.User;
import com.teaching.backend.domain.user.enums.TeacherPersona;
import com.teaching.backend.domain.user.exception.UserErrorCode;
import com.teaching.backend.domain.user.exception.UserException;
import com.teaching.backend.domain.user.repository.UserRepository;
import com.teaching.backend.global.ai.openai.OpenAiClient;
import com.teaching.backend.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class HighlightAnalysisService {

    private final MaterialHighlightRepository materialHighlightRepository;
    private final MaterialAnalysisRepository materialAnalysisRepository;
    private final AiGuideRepository aiGuideRepository;
    private final OpenAiClient openAiClient;
    private final HighlightAnalysisPromptGenerator highlightPromptGenerator;
    private final UserRepository userRepository;

    public HighlightAnalysisResponse getOrGenerateAiGuide(
            Long userId, Long materialId, Long highlightId
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
        GuideType guideType = toGuideType(user.getTeacherPersona());

        MaterialHighlight highlight = materialHighlightRepository.findById(highlightId)
                .orElseThrow(() -> new GeneralException(TeachingMapErrorCode.HIGHLIGHT_NOT_FOUND));

        validateHighlightBelongsToMaterial(highlight, materialId);

        return aiGuideRepository.findByMaterialHighlightIdAndGuideType(highlightId, guideType)
                .map(HighlightAnalysisResponse::from)
                .orElseGet(() -> generateAndSaveAiGuide(highlight, guideType, materialId));
    }

    private HighlightAnalysisResponse generateAndSaveAiGuide(
            MaterialHighlight highlight, GuideType guideType, Long materialId
    ) {
        MaterialAnalysis analysis = materialAnalysisRepository.findByMaterialId(materialId)
                .orElseThrow(() -> new GeneralException(TeachingMapErrorCode.MATERIAL_ANALYSIS_NOT_FOUND));

        String systemPrompt = highlightPromptGenerator.buildSystemPrompt();
        String userMessage = highlightPromptGenerator.buildUserMessage(
                guideType, analysis.getDetailAnalysis(), highlight.getHighlightText(), highlight.getHighlightType().name()
        );
        String content = openAiClient.chatComplete(systemPrompt, userMessage);

        AiGuideContentType type = (highlight.getHighlightType() == HighlightType.MAIN)
                ? AiGuideContentType.MAIN
                : AiGuideContentType.CAUTION;

        AiGuide saved = aiGuideRepository.save(
                AiGuide.create(highlight, "v1.2", guideType, type, highlight.getHighlightText(), content)
        );
        return HighlightAnalysisResponse.from(saved);
    }

    private void validateHighlightBelongsToMaterial(MaterialHighlight highlight, Long materialId) {
        if (!highlight.getMaterialChunk().getMaterial().getId().equals(materialId)) {
            throw new GeneralException(TeachingMapErrorCode.HIGHLIGHT_MATERIAL_MISMATCH);
        }
    }

    private GuideType toGuideType(TeacherPersona persona) {
        return switch (persona) {
            case FRIENDLY -> GuideType.FRIENDLY;
            case STRICT -> GuideType.STRICT;
            case CHEERING -> GuideType.ENCOURAGING;
        };
    }
}