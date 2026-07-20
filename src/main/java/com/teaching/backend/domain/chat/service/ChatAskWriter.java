package com.teaching.backend.domain.chat.service;

import com.teaching.backend.domain.chat.entity.ChatMessage;
import com.teaching.backend.domain.chat.entity.ChatRoom;
import com.teaching.backend.domain.chat.entity.ChatSource;
import com.teaching.backend.domain.chat.repository.ChatMessageRepository;
import com.teaching.backend.domain.chat.repository.ChatSourceRepository;
import com.teaching.backend.domain.material.entity.MaterialChunk;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// ask()의 외부 호출(벡터 검색, LLM 답변 생성) 이후 결과를 짧은 트랜잭션 안에서 저장하는 전용 컴포넌트.
// ChatMessageService의 메서드로 두면 self-invocation 때문에 @Transactional이 적용되지 않아 별도 빈으로 분리함.
@Component
@RequiredArgsConstructor
@Transactional
class ChatAskWriter {

    private final ChatRoomService chatRoomService;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatSourceRepository chatSourceRepository;

    AskResult write(
            Long chatRoomId,
            Long userId,
            String userContent,
            String answer,
            boolean isFallback,
            List<MaterialChunk> relevantChunks
    ) {
        // 외부 호출(검색/LLM) 도중 방이 삭제되는 경우를 대비해 쓰기 시점에 다시 조회
        ChatRoom chatRoom = chatRoomService.getChatRoom(chatRoomId, userId);

        ChatMessage userMessage = chatMessageRepository.save(
                ChatMessage.createUserMessage(chatRoom, userContent)
        );

        // isFallback은 LLM 답변 텍스트를 파싱하는 게 아니라 "근거로 삼을 청크가 있었는가"를 구조적으로 반영
        ChatMessage aiMessage = chatMessageRepository.save(
                isFallback
                        ? ChatMessage.createAiFallbackMessage(chatRoom, answer)
                        : ChatMessage.createAiMessage(chatRoom, answer)
        );

        chatRoom.updateLastMessageAt(aiMessage.getCreatedAt());

        List<ChatSource> aiMessageSources = relevantChunks.stream()
                .map(chunk -> chatSourceRepository.save(
                        ChatSource.create(chunk, aiMessage, citedAtOf(chunk))
                ))
                .toList();

        // TODO: 무료 회원 당일 남은 횟수 계산 로직 확정 전까지 무제한(null)으로 응답
        Integer remainingCount = null;

        return new AskResult(chatRoom, userMessage, aiMessage, aiMessageSources, remainingCount);
    }

    // ChatSource.citedAt은 NOT NULL이라, MaterialChunk에 위치 정보(position)가 아직 없는 경우 대체 값을 채워줌
    private String citedAtOf(MaterialChunk chunk) {
        return chunk.getPosition() != null ? chunk.getPosition() : ("청크 " + chunk.getChunkIndex());
    }
}
