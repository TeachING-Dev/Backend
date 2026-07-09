package com.teaching.backend.controller;

import com.teaching.backend.converter.MessageConverter;
import com.teaching.backend.dto.request.MessageCreateRequest;
import com.teaching.backend.dto.response.AskResponse;
import com.teaching.backend.dto.response.ChatRoomHistoryResponse;
import com.teaching.backend.global.auth.MockAuth;
import com.teaching.backend.global.response.ApiResponse;
import com.teaching.backend.global.response.SuccessCode;
import com.teaching.backend.service.AskResult;
import com.teaching.backend.service.ChatMessageService;
import com.teaching.backend.service.ChatRoomHistoryResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/chatrooms/{chatRoomId}/messages")
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    @GetMapping
    public ApiResponse<ChatRoomHistoryResponse> getMessages(@PathVariable Long chatRoomId) {
        Long userId = MockAuth.CURRENT_USER_ID;

        ChatRoomHistoryResult result = chatMessageService.getMessages(chatRoomId, userId);
        ChatRoomHistoryResponse response = MessageConverter.toHistoryResponse(result);

        return ApiResponse.onSuccess(SuccessCode.CHATROOM_HISTORY_OK, response);
    }

    @PostMapping
    public ApiResponse<AskResponse> ask(
            @PathVariable Long chatRoomId,
            @RequestBody MessageCreateRequest request
    ) {
        Long userId = MockAuth.CURRENT_USER_ID;

        AskResult result = chatMessageService.ask(chatRoomId, userId, request);
        AskResponse response = MessageConverter.toAskResponse(result);

        boolean isFallback = Boolean.TRUE.equals(result.aiMessage().getIsFallback());
        SuccessCode successCode = isFallback ? SuccessCode.ANSWER_FALLBACK : SuccessCode.ANSWER_OK;

        return ApiResponse.onSuccess(successCode, response);
    }
}
