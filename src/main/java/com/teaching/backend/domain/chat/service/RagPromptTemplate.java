package com.teaching.backend.domain.chat.service;

import com.teaching.backend.domain.material.entity.MaterialChunk;

import java.util.List;
import java.util.stream.Collectors;

// RAG 기반 LLM 호출에 사용할 시스템 프롬프트와 fallback 문구 상수 모음
public class RagPromptTemplate {

    private RagPromptTemplate() {
    }

    public static final String FALLBACK_PREFIX = "내 자료에는 없지만, 일반적인 지식에 따르면...";

    private static final String TEMPLATE = """
            System Role:
            당신은 사용자가 저장한 지식 베이스만을 바탕으로 답변하는 '지식 전문가'입니다. 반드시 제공된 [참조 자료] 내에서만 근거를 찾아 답변하십시오.

            Task:
            [사용자의 질문]에 대해 [참조 자료]를 활용하여 답변하십시오.

            [답변 규칙]
            근거 기반: 반드시 제공된 [참조 자료]에 명시된 내용만 사용하여 답변하십시오.
            출처 표기: 답변의 문장 끝마다 [출처: 자료 제목, 위치/타임라인]을 반드시 명시하십시오.
            거부 원칙: 만약 [참조 자료]에 질문에 대한 내용이 없다면, 다른 정보를 지어내지 말고 다음 문장으로 답변을 시작하십시오: "%s" 그 이후에 알고 있는 일반 상식을 제공하십시오.

            [참조 자료]
            %s

            예시: [자료명: Node.js 원리, 출처: 12행], [자료명: JS 강의 영상, 출처: 02:30]
            """;

    public static String buildSystemPrompt(List<MaterialChunk> chunks) {
        return TEMPLATE.formatted(FALLBACK_PREFIX, formatChunks(chunks));
    }

    private static String formatChunks(List<MaterialChunk> chunks) {
        if (chunks.isEmpty()) {
            return "(제공된 참조 자료 없음)";
        }
        return chunks.stream()
                .map(chunk -> "[자료명: %s, 출처: %s] %s".formatted(
                        chunk.getMaterial().getTitle(),
                        chunk.getPosition() != null ? chunk.getPosition() : ("청크 " + chunk.getChunkIndex()),
                        chunk.getChunkText()
                ))
                .collect(Collectors.joining("\n\n"));
    }
}
