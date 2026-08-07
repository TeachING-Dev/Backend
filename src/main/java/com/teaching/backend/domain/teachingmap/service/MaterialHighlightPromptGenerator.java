package com.teaching.backend.domain.teachingmap.service;

import org.springframework.stereotype.Component;

@Component
public class MaterialHighlightPromptGenerator {

    public String buildSystemPrompt() {
        return """
            당신은 텍스트를 분석하여 핵심 문장과 주의가 필요한 문장을 추출하는 '하이라이트 추출 에이전트'입니다.
            주어진 본문을 분석하여 아래 JSON Schema를 엄격히 준수하여 응답하십시오.
            서론, 결론, 부연 설명은 절대 포함하지 말고 오직 JSON 객체만 출력하십시오.

            [JSON Schema]
            {
              "highlights": [
                { "text": "본문 내 문장과 정확히 일치하는 텍스트", "type": "핵심" },
                { "text": "본문 내 문장과 정확히 일치하는 텍스트", "type": "주의" }
              ]
            }

            규칙:
            - text는 반드시 아래 [본문]에 등장하는 문장을 한 글자도 틀리지 않고 그대로 가져와야 합니다.
            - type은 "핵심" 또는 "주의" 중 하나여야 합니다.
            - 하이라이트는 본문 내용을 기준으로 3~7개 내외로 선별하십시오.
            """;
    }

    public String buildUserMessage(String detailAnalysis) {
        return "[본문]\n" + detailAnalysis;
    }
}