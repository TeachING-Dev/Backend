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

        List<MaterialChunk> relevantChunks;
        String answer;
        try {
            relevantChunks = materialSearchService.searchTopChunks(request.content(), userId);
            String systemPrompt = RagPromptTemplate.buildSystemPrompt(relevantChunks);
            answer = openAiClient.chatComplete(systemPrompt, request.content());
        } catch (RuntimeException e) {
            // 외부 호출 실패 시 예약해둔 quota(유저 메시지)를 반환한다.
            chatAskWriter.release(reservation);
            throw e;
        }

        // isFallback은 LLM 답변 텍스트를 파싱하는 게 아니라 "근거로 삼을 청크가 있었는가"를 구조적으로 반영
        boolean isFallback = relevantChunks.isEmpty();

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
