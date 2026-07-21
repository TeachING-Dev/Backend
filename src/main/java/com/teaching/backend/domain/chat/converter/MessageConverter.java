package com.teaching.backend.domain.chat.converter;

import com.teaching.backend.domain.chat.dto.AiMessageResponse;
import com.teaching.backend.domain.chat.dto.AskResponse;
import com.teaching.backend.domain.chat.dto.ChatRoomHistoryResponse;
import com.teaching.backend.domain.chat.dto.ChatSourceResponse;
import com.teaching.backend.domain.chat.dto.MessageResponse;
import com.teaching.backend.domain.chat.dto.UserMessageResponse;
import com.teaching.backend.domain.chat.entity.ChatMessage;
import com.teaching.backend.domain.chat.entity.ChatSource;
import com.teaching.backend.domain.chat.service.AskResult;
import com.teaching.backend.domain.chat.service.ChatRoomHistoryResult;
import com.teaching.backend.domain.material.entity.Material;

import java.util.List;

// ChatMessage 및 질문/답변 결과를 응답 DTO로 변환하는 컨버터
public class MessageConverter {

    private MessageConverter() {
    }

    public static MessageResponse toResponse(ChatMessage chatMessage, List<ChatSource> sources) {
        return new MessageResponse(
                chatMessage.getId(),
                chatMessage.getRole(),
                chatMessage.getContent(),
                chatMessage.getIsFallback(),
                toSourceResponses(sources),
                chatMessage.getCreatedAt()
        );
    }

    public static ChatRoomHistoryResponse toHistoryResponse(ChatRoomHistoryResult result) {
        return new ChatRoomHistoryResponse(
                result.chatRoom().getId(),
                result.chatRoom().getTitle(),
                result.messages().stream()
                        .map(message -> toResponse(
                                message,
                                result.sourcesByMessageId().getOrDefault(message.getId(), List.of())
                        ))
                        .toList()
        );
    }

    public static UserMessageResponse toUserMessageResponse(ChatMessage chatMessage) {
        return new UserMessageResponse(
                chatMessage.getId(),
                chatMessage.getContent(),
                chatMessage.getCreatedAt()
        );
    }

    public static AiMessageResponse toAiMessageResponse(ChatMessage chatMessage, List<ChatSource> sources) {
        return new AiMessageResponse(
                chatMessage.getId(),
                chatMessage.getContent(),
                chatMessage.getIsFallback(),
                toSourceResponses(sources),
                chatMessage.getCreatedAt()
        );
    }

    public static AskResponse toAskResponse(AskResult result) {
        return new AskResponse(
                result.chatRoom().getTitle(),
                toUserMessageResponse(result.userMessage()),
                toAiMessageResponse(result.aiMessage(), result.aiMessageSources()),
                result.remainingCount()
        );
    }

    private static List<ChatSourceResponse> toSourceResponses(List<ChatSource> sources) {
        return sources.stream().map(MessageConverter::toSourceResponse).toList();
    }

    private static ChatSourceResponse toSourceResponse(ChatSource source) {
        Material material = source.getMaterialChunk().getMaterial();

        return new ChatSourceResponse(
                source.getId(),
                material.getId(),
                material.getTitle(),
                material.getFolder().getName(),
                material.getOriginalUrl(),
                source.getMaterialChunk().getChunkText(),
                source.getCitedAt()
        );
    }
}
