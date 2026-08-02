package com.teaching.backend.domain.chat.service;

import com.teaching.backend.domain.chat.dto.MessageCreateRequest;
import com.teaching.backend.domain.chat.entity.ChatMessage;
import com.teaching.backend.domain.chat.entity.ChatRoom;
import com.teaching.backend.domain.chat.entity.ChatSource;
import com.teaching.backend.domain.chat.repository.ChatMessageRepository;
import com.teaching.backend.domain.chat.repository.ChatSourceRepository;
import com.teaching.backend.domain.material.entity.MaterialChunk;
import com.teaching.backend.domain.material.service.MaterialSearchService;
import com.teaching.backend.global.ai.openai.OpenAiClient;
import com.teaching.backend.global.apiPayload.code.GlobalErrorCode;
import com.teaching.backend.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

// 메시지 저장, 벡터 검색(RAG), LLM 답변 생성 및 출처 저장을 담당하는 서비스
@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private static final int CONTENT_MAX_LENGTH = 2000;
    static final int FREE_DAILY_QUESTION_LIMIT = 5;
    static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ChatMessageRepository chatMessageRepository;
    private final ChatSourceRepository chatSourceRepository;
    private final ChatRoomService chatRoomService;
    private final MaterialSearchService materialSearchService;
    private final OpenAiClient openAiClient;
    private final ChatAskWriter chatAskWriter;
    private final AggregateQuestionAnswerer aggregateQuestionAnswerer;

    @Transactional(readOnly = true)
    public ChatRoomHistoryResult getMessages(Long chatRoomId, Long userId) {
        ChatRoom chatRoom = chatRoomService.getChatRoom(chatRoomId, userId);
        List<ChatMessage> messages = chatMessageRepository.findByChatRoomIdOrderByCreatedAtAsc(chatRoomId);
        Map<Long, List<ChatSource>> sourcesByMessageId = findSourcesByMessageId(messages);

        return new ChatRoomHistoryResult(chatRoom, messages, sourcesByMessageId);
    }

    private Map<Long, List<ChatSource>> findSourcesByMessageId(List<ChatMessage> messages) {
        if (messages.isEmpty()) {
            return Map.of();
        }

        List<Long> messageIds = messages.stream().map(ChatMessage::getId).toList();
        return chatSourceRepository.findAllByChatMessageIdInWithMaterial(messageIds).stream()
                .collect(Collectors.groupingBy(source -> source.getChatMessage().getId()));
    }

    // 벡터 검색(Qdrant)과 LLM 호출(OpenAI)이 외부 네트워크 호출이라 트랜잭션 없이 수행한다.
    // 비용이 드는 외부 호출 전에 ChatAskWriter.reserve()가 유저 행을 잠그고 무료 회원의 당일 질문 한도를
    // 원자적으로 예약(유저 메시지 선저장)해, 동시 요청이 같은 잔여 quota를 중복 통과하지 못하게 막는다.
    public AskResult ask(Long chatRoomId, Long userId, MessageCreateRequest request) {
        validateContent(request.content());

        ChatAskWriter.Reservation reservation = chatAskWriter.reserve(chatRoomId, userId, request.content());

        // 개수/최근 자료 같은 집계형 질문은 특정 청크를 인용할 수 없어 RAG 흐름과 근본적으로 다르므로
        // 가장 먼저 확인한다. 외부 LLM/벡터 호출 없이 구조화된 DB 조회만으로 즉시 답한다.
        Optional<String> aggregateAnswer = aggregateQuestionAnswerer.tryAnswer(request.content(), userId);
        if (aggregateAnswer.isPresent()) {
            return chatAskWriter.finalizeAnswer(reservation, aggregateAnswer.get(), false, List.of());
        }

        List<MaterialChunk> relevantChunks;
        String answer;
        try {
            // 1순위: 질문에 언급된 자료 제목/태그를 RDB에서 구조화 조회(메타 질문에 강함).
            // 2순위: 질문의 키워드가 청크 본문에 그대로 있는지 직접 조회(임베딩 유사도가 임계값을
            //        못 넘어 놓치는, 단어는 있는데 못 찾는 사각지대를 메움).
            // 3순위: Qdrant 임베딩 유사도 검색(단어 자체는 없는 의미/맥락 질문에 강함).
            // 4순위: 자료 제목/태그 자체를 임베딩해 질문과 의미 비교(동의어·우회 표현 메타 질문에 대응).
            relevantChunks = materialSearchService.searchByMetadata(request.content(), userId);
            if (relevantChunks.isEmpty()) {
                relevantChunks = materialSearchService.searchByContentKeyword(request.content(), userId);
            }
            if (relevantChunks.isEmpty()) {
                relevantChunks = materialSearchService.searchTopChunks(request.content(), userId);
            }
            if (relevantChunks.isEmpty()) {
                relevantChunks = materialSearchService.searchByTitleTagSimilarity(request.content(), userId);
            }
            String systemPrompt = RagPromptTemplate.buildSystemPrompt(relevantChunks);
            answer = openAiClient.chatComplete(systemPrompt, request.content());
        } catch (RuntimeException e) {
            // 외부 호출 실패 시 예약해둔 quota(유저 메시지)를 반환한다.
            chatAskWriter.release(reservation);
            throw e;
        }

        // 청크가 검색됐어도, LLM이 그 내용으론 답을 못 찾아 프롬프트 규칙대로 fallback 문구로
        // 답했다면 실질적으로는 fallback이다. 검색 결과 유무만으로 판단하면 관련 없는 자료가
        // "답변 출처"로 잘못 붙는 문제가 있어, 실제 답변 텍스트도 함께 확인한다.
        // FALLBACK_PREFIX 전체가 아니라 짧은 FALLBACK_MARKER로 비교하는 이유는 RagPromptTemplate 참고.
        boolean isFallback = relevantChunks.isEmpty()
                || answer.strip().startsWith(RagPromptTemplate.FALLBACK_MARKER);

        return chatAskWriter.finalizeAnswer(reservation, answer, isFallback, relevantChunks);
    }

    private void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new GeneralException(GlobalErrorCode.BAD_REQUEST);
        }
        if (content.length() > CONTENT_MAX_LENGTH) {
            throw new GeneralException(GlobalErrorCode.BAD_REQUEST);
        }
    }
}
