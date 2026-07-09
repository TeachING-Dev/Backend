package com.teaching.backend.converter;

import com.teaching.backend.dto.response.AiMessageResponse;
import com.teaching.backend.dto.response.AskResponse;
import com.teaching.backend.dto.response.ChatRoomHistoryResponse;
import com.teaching.backend.dto.response.MessageResponse;
import com.teaching.backend.dto.response.UserMessageResponse;
import com.teaching.backend.entity.ChatMessage;
import com.teaching.backend.service.AskResult;
import com.teaching.backend.service.ChatRoomHistoryResult;

import java.util.List;

public class MessageConverter {

    private MessageConverter() {
    }

    public static MessageResponse toResponse(ChatMessage chatMessage) {
        return new MessageResponse(
                chatMessage.getId(),
                chatMessage.getRole(),
                chatMessage.getContent(),
                chatMessage.getIsFallback(),
                // TODO: Materials/Folders 도메인 구축 후 ChatSources 조인 결과로 대체
                List.of(),
                chatMessage.getCreatedAt()
        );
    }

    public static ChatRoomHistoryResponse toHistoryResponse(ChatRoomHistoryResult result) {
        return new ChatRoomHistoryResponse(
                result.chatRoom().getId(),
                result.chatRoom().getTitle(),
                result.messages().stream()
                        .map(MessageConverter::toResponse)
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

    public static AiMessageResponse toAiMessageResponse(ChatMessage chatMessage) {
        return new AiMessageResponse(
                chatMessage.getId(),
                chatMessage.getContent(),
                chatMessage.getIsFallback(),
                // TODO: Materials/Folders 도메인 구축 후 ChatSources 조인 결과로 대체
                List.of(),
                chatMessage.getCreatedAt()
        );
    }

    public static AskResponse toAskResponse(AskResult result) {
        return new AskResponse(
                result.chatRoom().getTitle(),
                toUserMessageResponse(result.userMessage()),
                toAiMessageResponse(result.aiMessage()),
                result.remainingCount()
        );
    }
}
