package com.teaching.backend.domain.material.service.ai;

import com.teaching.backend.domain.material.dto.ai.MaterialAiStageContext;
import com.teaching.backend.domain.material.dto.ai.MaterialAiStageResult;
import com.teaching.backend.domain.material.enums.MaterialAiStageType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaterialAiAnalysisStageRegistryTest {

    @Test
    void returnsStagesInExplicitStageTypeOrder() {
        MaterialAiAnalysisStage stage = stage(MaterialAiStageType.CONTENT_ANALYSIS);

        MaterialAiAnalysisStageRegistry registry = new MaterialAiAnalysisStageRegistry(List.of(stage));

        assertThat(registry.stagesInOrder()).containsExactly(stage);
    }

    @Test
    void detectsDuplicateStageRegistration() {
        MaterialAiAnalysisStage first = stage(MaterialAiStageType.CONTENT_ANALYSIS);
        MaterialAiAnalysisStage second = stage(MaterialAiStageType.CONTENT_ANALYSIS);

        assertThatThrownBy(() -> new MaterialAiAnalysisStageRegistry(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void detectsMissingRequiredStage() {
        assertThatThrownBy(() -> new MaterialAiAnalysisStageRegistry(List.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    private MaterialAiAnalysisStage stage(MaterialAiStageType type) {
        return new MaterialAiAnalysisStage() {
            @Override
            public MaterialAiStageType type() {
                return type;
            }

            @Override
            public MaterialAiStageResult execute(MaterialAiStageContext context) {
                return null;
            }
        };
    }
}
