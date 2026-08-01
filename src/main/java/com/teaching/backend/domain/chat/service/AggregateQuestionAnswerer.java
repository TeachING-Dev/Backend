package com.teaching.backend.domain.chat.service;

import com.teaching.backend.domain.folder.entity.Folder;
import com.teaching.backend.domain.folder.repository.FolderRepository;
import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.repository.MaterialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

// 자료 개수/최근 자료/목록 조회처럼 특정 청크를 인용할 수 없는 집계형 질문을 감지해 구조화된 DB 조회로 직접 답한다.
// RAG(청크 인용) 흐름과 근본적으로 다른 응답 방식이라 ChatMessageService.ask()에서 가장 먼저 시도된다.
@Component
@RequiredArgsConstructor
class AggregateQuestionAnswerer {

    private static final int MAX_LISTED_MATERIALS = 20;

    private static final Pattern COUNT_PATTERN = Pattern.compile("(몇\\s*개|개수|총\\s*(몇|자료))");
    private static final Pattern MOST_RECENT_PATTERN = Pattern.compile("(가장\\s*최근|최신|마지막으로\\s*저장)");
    private static final Pattern LIST_PATTERN = Pattern.compile("(목록|리스트|뭐\\s*있|무슨\\s*자료|어떤\\s*자료)");

    private final MaterialRepository materialRepository;
    private final FolderRepository folderRepository;

    // 집계형 질문이 아니면 Optional.empty() — 호출부가 기존 RAG 흐름으로 계속 진행하게 한다.
    // "최근"이 가장 구체적인 의도이므로 먼저 검사하고, "목록"(나열)과 "개수"(집계) 순으로 검사한다.
    Optional<String> tryAnswer(String question, Long userId) {
        if (MOST_RECENT_PATTERN.matcher(question).find()) {
            return Optional.of(mostRecentAnswer(userId));
        }
        if (LIST_PATTERN.matcher(question).find()) {
            return Optional.of(listAnswer(question, userId));
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

    // 질문에 사용자의 폴더명이 언급됐으면 그 폴더로 한정해서 나열하고, 아니면 전체 자료를 나열한다.
    private String listAnswer(String question, Long userId) {
        Optional<Folder> mentionedFolder = folderRepository.findAllByUser_Id(userId, Sort.unsorted()).stream()
                .filter(folder -> question.contains(folder.getName()))
                .findFirst();

        if (mentionedFolder.isPresent()) {
            Folder folder = mentionedFolder.get();
            List<String> titles = materialRepository.findAllByFolder_Id(folder.getId()).stream()
                    .map(Material::getTitle)
                    .toList();
            return titles.isEmpty()
                    ? "'" + folder.getName() + "' 폴더에는 아직 저장된 자료가 없습니다."
                    : "'" + folder.getName() + "' 폴더에 있는 자료는 " + formatTitles(titles) + "입니다.";
        }

        List<String> titles = materialRepository.findAllByUser_Id(userId, Sort.unsorted()).stream()
                .map(Material::getTitle)
                .toList();
        return titles.isEmpty()
                ? "아직 저장하신 자료가 없습니다."
                : "저장하신 자료는 " + formatTitles(titles) + "입니다.";
    }

    private String formatTitles(List<String> titles) {
        if (titles.size() <= MAX_LISTED_MATERIALS) {
            return String.join(", ", titles);
        }
        List<String> visible = titles.subList(0, MAX_LISTED_MATERIALS);
        return String.join(", ", visible) + " 외 " + (titles.size() - MAX_LISTED_MATERIALS) + "개";
    }
}
