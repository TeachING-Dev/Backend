package com.teaching.backend.service;

import com.teaching.backend.dto.request.MessageCreateRequest;
import com.teaching.backend.entity.ChatMessage;
import com.teaching.backend.entity.ChatRoom;
import com.teaching.backend.entity.MessageRole;
import com.teaching.backend.global.exception.ErrorCode;
import com.teaching.backend.global.exception.GeneralException;
import com.teaching.backend.prompt.RagPromptTemplate;
import com.teaching.backend.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatMessageService {

    private static final int TITLE_MAX_LENGTH = 15;
    private static final int CONTENT_MAX_LENGTH = 2000;

    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomService chatRoomService;

    public ChatRoomHistoryResult getMessages(Long chatRoomId, Long userId) {
        ChatRoom chatRoom = chatRoomService.getChatRoom(chatRoomId, userId);
        List<ChatMessage> messages = chatMessageRepository.findByChatRoomIdOrderByCreatedAtAsc(chatRoomId);

        return new ChatRoomHistoryResult(chatRoom, messages);
    }

    @Transactional
    public AskResult ask(Long chatRoomId, Long userId, MessageCreateRequest request) {
        validateContent(request.content());

        ChatRoom chatRoom = chatRoomService.getChatRoom(chatRoomId, userId);

        // TODO: 무료 회원 당일 질문 횟수(5회) 제한(DAILY_QUESTION_LIMIT_EXCEEDED) 정책/사용량 저장소 확정 후 적용

        ChatMessage userMessage = chatMessageRepository.save(
                ChatMessage.builder()
                        .chatRoom(chatRoom)
                        .role(MessageRole.USER)
                        .content(request.content())
                        .isFallback(null)
                        .build()
        );

        if (chatRoom.getTitle() == null) {
            chatRoom.updateTitle(generateTitle(request.content()));
        }

        // TODO: 임베딩 검색(MaterialChunk) + LLM 연동 전까지 항상 근거 자료 없음(fallback)으로 처리
        ChatMessage aiMessage = chatMessageRepository.save(
                ChatMessage.builder()
                        .chatRoom(chatRoom)
                        .role(MessageRole.AI)
                        .content(generateFallbackAnswer(request.content()))
                        .isFallback(true)
                        .build()
        );

        chatRoom.updateLastMessageAt(aiMessage.getCreatedAt());

        // TODO: 무료 회원 당일 남은 횟수 계산 로직 확정 전까지 무제한(null)으로 응답
        Integer remainingCount = null;

        return new AskResult(chatRoom, userMessage, aiMessage, remainingCount);
    }

    private void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new GeneralException(ErrorCode.CONTENT_REQUIRED);
        }
        if (content.length() > CONTENT_MAX_LENGTH) {
            throw new GeneralException(ErrorCode.BAD_REQUEST);
        }
    }

    private String generateTitle(String content) {
        return content.length() > TITLE_MAX_LENGTH
                ? content.substring(0, TITLE_MAX_LENGTH)
                : content;
    }

    // TODO: RagPromptTemplate.SYSTEM_PROMPT + MaterialChunk 검색 결과로 실제 LLM 호출하도록 교체
    private String generateFallbackAnswer(String question) {
        return RagPromptTemplate.FALLBACK_PREFIX + " (RAG/LLM 연동 전 TODO 스텁 응답입니다.)";
    }
}
