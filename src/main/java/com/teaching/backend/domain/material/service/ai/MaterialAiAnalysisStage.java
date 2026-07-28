package com.teaching.backend.domain.material.service.ai;

import com.teaching.backend.domain.material.dto.ai.MaterialAiStageContext;
import com.teaching.backend.domain.material.dto.ai.MaterialAiStageResult;
import com.teaching.backend.domain.material.enums.MaterialAiStageType;

public interface MaterialAiAnalysisStage {

    MaterialAiStageType type();

    MaterialAiStageResult execute(MaterialAiStageContext context);
}
