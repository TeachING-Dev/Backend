package com.teaching.backend.domain.chat.service;

import com.teaching.backend.domain.chat.dto.MessageCreateRequest;
import com.teaching.backend.domain.chat.entity.ChatMessage;
import com.teaching.backend.domain.chat.entity.ChatRoom;
import com.teaching.backend.domain.chat.entity.ChatSource;
import com.teaching.backend.domain.chat.enums.ChatRole;
import com.teaching.backend.domain.chat.exception.ChatErrorCode;
import com.teaching.backend.domain.chat.exception.ChatException;
import com.teaching.backend.domain.chat.repository.ChatMessageRepository;
import com.teaching.backend.domain.chat.repository.ChatSourceRepository;
import com.teaching.backend.domain.material.entity.MaterialChunk;
import com.teaching.backend.domain.material.service.MaterialSearchService;
import com.teaching.backend.domain.user.entity.User;
import com.teaching.backend.domain.user.enums.MembershipType;
import com.teaching.backend.domain.user.repository.UserRepository;
import com.teaching.backend.global.ai.openai.OpenAiClient;
import com.teaching.backend.global.apiPayload.code.GlobalErrorCode;
import com.teaching.backend.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
    private final UserRepository userRepository;

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

    // 벡터 검색(Qdrant)과 LLM 호출(OpenAI)이 외부 네트워크 호출이라 트랜잭션 없이 수행하고,
    // 메시지/출처 저장만 ChatAskWriter의 짧은 트랜잭션에 위임해 커넥션 점유 시간을 최소화한다.
    public AskResult ask(Long chatRoomId, Long userId, MessageCreateRequest request) {
        validateContent(request.content());

        // 조기 검증: 방이 없거나 소유자가 아니면 외부 호출 전에 실패시킨다.
        chatRoomService.getChatRoom(chatRoomId, userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(GlobalErrorCode.NOT_FOUND));

        // 비용이 드는 외부 호출(벡터 검색, LLM) 전에 무료 회원의 당일 질문 횟수 한도를 먼저 검증한다.
        if (user.getMembershipType() == MembershipType.FREE
                && countTodayUserMessages(userId) >= FREE_DAILY_QUESTION_LIMIT) {
            throw new ChatException(ChatErrorCode.DAILY_QUESTION_LIMIT_EXCEEDED);
        }

        List<MaterialChunk> relevantChunks = materialSearchService.searchTopChunks(request.content(), userId);

        String systemPrompt = RagPromptTemplate.buildSystemPrompt(relevantChunks);
        String answer = openAiClient.chatComplete(systemPrompt, request.content());

        // isFallback은 LLM 답변 텍스트를 파싱하는 게 아니라 "근거로 삼을 청크가 있었는가"를 구조적으로 반영
        boolean isFallback = relevantChunks.isEmpty();

        return chatAskWriter.write(chatRoomId, userId, request.content(), answer, isFallback, relevantChunks);
    }

    private long countTodayUserMessages(Long userId) {
        return chatMessageRepository.countByChatRoom_User_IdAndRoleAndCreatedAtGreaterThanEqual(
                userId, ChatRole.USER, LocalDate.now(KST).atStartOfDay());
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
