package com.teaching.backend.domain.material.service.ai;

import com.teaching.backend.domain.material.enums.MaterialAiStageType;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class MaterialAiAnalysisStageRegistry {

    private final List<MaterialAiAnalysisStage> stages;

    public MaterialAiAnalysisStageRegistry(List<MaterialAiAnalysisStage> stages) {
        Map<MaterialAiStageType, MaterialAiAnalysisStage> stageByType = new EnumMap<>(MaterialAiStageType.class);
        for (MaterialAiAnalysisStage stage : stages) {
            MaterialAiAnalysisStage duplicated = stageByType.put(stage.type(), stage);
            if (duplicated != null) {
                throw new IllegalStateException("Duplicate MaterialAiAnalysisStage for " + stage.type());
            }
        }
        for (MaterialAiStageType stageType : MaterialAiStageType.values()) {
            if (!stageByType.containsKey(stageType)) {
                throw new IllegalStateException("Missing MaterialAiAnalysisStage for " + stageType);
            }
        }
        this.stages = stageByType.values()
                .stream()
                .sorted(Comparator.comparingInt(stage -> stage.type().order()))
                .toList();
    }

    public List<MaterialAiAnalysisStage> stagesInOrder() {
        return stages;
    }
}
