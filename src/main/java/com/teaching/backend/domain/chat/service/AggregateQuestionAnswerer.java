package com.teaching.backend.domain.chat.service;

import com.teaching.backend.domain.material.repository.MaterialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

// 자료 개수/최근 자료 조회처럼 특정 청크를 인용할 수 없는 집계형 질문을 감지해 구조화된 DB 조회로 직접 답한다.
// RAG(청크 인용) 흐름과 근본적으로 다른 응답 방식이라 ChatMessageService.ask()에서 가장 먼저 시도된다.
@Component
@RequiredArgsConstructor
class AggregateQuestionAnswerer {

    private static final Pattern COUNT_PATTERN = Pattern.compile("(몇\\s*개|개수|총\\s*(몇|자료))");
    private static final Pattern MOST_RECENT_PATTERN = Pattern.compile("(가장\\s*최근|최신|마지막으로\\s*저장)");

    private final MaterialRepository materialRepository;

    // 집계형 질문이 아니면 Optional.empty() — 호출부가 기존 RAG 흐름으로 계속 진행하게 한다.
    // "최근" 계열이 "개수" 계열보다 더 구체적인 의도이므로 먼저 검사한다.
    Optional<String> tryAnswer(String question, Long userId) {
        if (MOST_RECENT_PATTERN.matcher(question).find()) {
            return Optional.of(mostRecentAnswer(userId));
        }
        if (COUNT_PATTERN.matcher(question).find()) {
            return Optional.of(countAnswer(userId));
        }
        return Optional.empty();
    }

    private String countAnswer(Long userId) {
        long count = materialRepository.countByUser_Id(userId);
        return "현재 저장하신 자료는 총 " + count + "개입니다.";
    }

    private String mostRecentAnswer(Long userId) {
        return materialRepository.findFirstByUser_IdOrderByCreatedAtDesc(userId)
                .map(material -> "가장 최근에 저장하신 자료는 '" + material.getTitle() + "'입니다.")
                .orElse("아직 저장하신 자료가 없습니다.");
    }
}
