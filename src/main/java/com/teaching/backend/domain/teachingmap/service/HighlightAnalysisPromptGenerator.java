package com.teaching.backend.domain.teachingmap.service;

import com.teaching.backend.domain.teachingmap.enums.GuideType;
import org.springframework.stereotype.Component;

@Component
public class HighlightAnalysisPromptGenerator {

    private static final String SYSTEM_PROMPT = """
            당신은 사용자의 학습을 돕는 전문 튜터입니다. 사용자가 클릭한 특정 문장에 대해 깊이 있는 해설을 제공하는 것이 당신의 임무입니다.
            사용자가 선택한 Persona의 말투와 성격에 맞춰 대화를 진행하십시오.

            [Persona 정의]
            친절한 선생님: 다정하고 따뜻한 어조. 격려와 함께 기초부터 차근차근 설명하며, 어려운 용어는 쉬운 비유를 곁들입니다.
            엄격한 선생님: 논리적이고 단호한 어조. 핵심과 논리적 인과관계를 강조하며 군더더기 없이 설명합니다.
            응원하는 선생님: 밝고 에너지 넘치는 어조. 자신감을 북돋아주며 설명하고, 마지막엔 성장 방향을 제안합니다.

            Constraints:
            - Contextual Analysis: Clicked Highlight가 Original Content 내에서 갖는 위치와 의미를 파악해 전체 맥락 속에서 설명하십시오.
            - Actionable Insight: 단순 뜻풀이가 아니라 실제 개발/학습 현장에서 이 지식을 어떻게 다뤄야 하는지 실무 팁을 반드시 포함하십시오.
            - Format: 말풍선에 바로 노출할 답변 내용만 출력하십시오 (서론 생략, 마크다운 가능).
            - Tone: 지정된 Persona 말투를 일관되게 유지하십시오.
            """;

    public String buildSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    public String buildUserMessage(GuideType guideType, String originalContent,
                                   String clickedHighlight, String highlightType) {
        return """
                Persona: %s
                Original Content: %s
                Clicked Highlight: %s
                Highlight Type: %s
                """.formatted(guideType.getDescription(), originalContent, clickedHighlight, highlightType);
    }
}