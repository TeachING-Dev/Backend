package com.teaching.backend.domain.material.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teaching.backend.domain.folder.entity.Folder;
import com.teaching.backend.domain.folder.repository.FolderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;

// URL 자료 AI 분석(short_summary/long_analysis/tags 등) system/user 프롬프트를 구성하는 빌더
// Spring Boot 4는 Jackson 3 ObjectMapper만 자동 구성하므로, Jackson 2 ObjectMapper는 직접 소유한다.
@Component
@RequiredArgsConstructor
public class MaterialAnalysisPromptBuilder {

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            System Role:
            당신은 웹 콘텐츠를 분석하여 데이터베이스에 저장하기 최적화된 형태로 가공하는 '콘텐츠 분석 에이전트'입니다.

            Task:
            제공된 URL과 본문 내용을 분석하여 아래 [JSON Schema]를 엄격히 준수하여 응답하십시오. 서론, 결론, 부연 설명은 절대 포함하지 말고 오직 JSON 객체만 출력하십시오.

            [데이터베이스 참고 정보]
            현재 사용자의 폴더 목록: %s

            [JSON Schema]
            {
              "short_summary": "3~5줄 내외의 핵심 요약",
              "long_analysis": "마크다운(Markdown) 형식을 사용한 상세 분석 (개요, 핵심 포인트, 결론을 헤더와 불렛포인트로 구조화)",
              "highlights": [
                {
                  "text": "long_analysis 본문 내에서 하이라이트할 핵심 문장 (마크다운 기호 ** 또는 # 등은 제외하고 순수 텍스트만 추출)",
                  "type": "MAIN" | "CAUTION"
                }
              ],
              "tags": ["태그1", "태그2", "태그3"],
              "recommended_folder": "제공된 현재 폴더 목록 중 1개 선택 (없는 경우 유사 카테고리 추천)"
            }

            Constraints (반드시 준수):
            - Source Only: 제공된 URL과 본문 내용 이외의 외부 지식은 배제하고 본문 내용만 분석하십시오.
            - Strict JSON: 오직 순수 JSON 객체만 작성하십시오. ```json 마크다운 코드 블록 표현을 포함하지 마십시오.
            - Markdown Syntax: long_analysis는 반드시 ##(제목), *(리스트), **(강조) 문법을 사용하십시오.
            - Highlight: long_analysis 작성이 끝난 후, 본문 안에서 중요한 문장 3~5개를 골라 highlights 배열에 넣으십시오.
            - Highlight Text: highlights의 text는 long_analysis에 포함된 문장이어야 하며, type은 반드시 "MAIN" 또는 "CAUTION"으로 작성하십시오.
            - Tags: 반드시 **3개 이상 5개 이하**의 서로 다른 핵심 태그를 추출하십시오.
            - Language: 모든 내용은 한국어로 작성하십시오.
            """;

    private final FolderRepository folderRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String buildSystemPrompt(Long userId) {
        List<Folder> folders = folderRepository.findAllByUser_Id(userId, Sort.by(Sort.Direction.ASC, "name"));
        return SYSTEM_PROMPT_TEMPLATE.formatted(writeFolderNamesAsJson(folders));
    }

    private String writeFolderNamesAsJson(List<Folder> folders) {
        try {
            return objectMapper.writeValueAsString(folders.stream().map(Folder::getName).toList());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("폴더 목록을 JSON으로 직렬화하는 데 실패했습니다.", e);
        }
    }

    public String buildUserMessage(String originalUrl, String content) {
        return "URL: " + originalUrl + "\n\n본문:\n" + content;
    }
}