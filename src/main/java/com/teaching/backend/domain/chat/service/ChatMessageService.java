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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// 메시지 저장, 벡터 검색(RAG), LLM 답변 생성 및 출처 저장을 담당하는 서비스
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatMessageService {

    private static final int CONTENT_MAX_LENGTH = 2000;

    private final ChatMessageRepository chatMessageRepository;
    private final ChatSourceRepository chatSourceRepository;
    private final ChatRoomService chatRoomService;
    private final MaterialSearchService materialSearchService;
    private final OpenAiClient openAiClient;

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

    @Transactional
    public AskResult ask(Long chatRoomId, Long userId, MessageCreateRequest request) {
        validateContent(request.content());

        ChatRoom chatRoom = chatRoomService.getChatRoom(chatRoomId, userId);

        // TODO: 무료 회원 당일 질문 횟수(5회) 제한(DAILY_QUESTION_LIMIT_EXCEEDED) 정책/사용량 저장소 확정 후 적용

        ChatMessage userMessage = chatMessageRepository.save(
                ChatMessage.createUserMessage(chatRoom, request.content())
        );

        List<MaterialChunk> relevantChunks = materialSearchService.searchTopChunks(request.content(), userId);

        String systemPrompt = RagPromptTemplate.buildSystemPrompt(relevantChunks);
        String answer = openAiClient.chatComplete(systemPrompt, request.content());

        // isFallback은 LLM 답변 텍스트를 파싱하는 게 아니라 "근거로 삼을 청크가 있었는가"를 구조적으로 반영
        boolean isFallback = relevantChunks.isEmpty();
        ChatMessage aiMessage = chatMessageRepository.save(
                isFallback
                        ? ChatMessage.createAiFallbackMessage(chatRoom, answer)
                        : ChatMessage.createAiMessage(chatRoom, answer)
        );

        chatRoom.updateLastMessageAt(aiMessage.getCreatedAt());

        List<ChatSource> aiMessageSources = relevantChunks.stream()
                .map(chunk -> chatSourceRepository.save(
                        ChatSource.create(chunk, aiMessage, chunk.getPosition())
                ))
                .toList();

        // TODO: 무료 회원 당일 남은 횟수 계산 로직 확정 전까지 무제한(null)으로 응답
        Integer remainingCount = null;

        return new AskResult(chatRoom, userMessage, aiMessage, aiMessageSources, remainingCount);
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
