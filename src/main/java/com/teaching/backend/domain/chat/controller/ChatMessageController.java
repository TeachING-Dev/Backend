package com.teaching.backend.domain.chat.controller;

import com.teaching.backend.domain.chat.converter.MessageConverter;
import com.teaching.backend.domain.chat.dto.AskResponse;
import com.teaching.backend.domain.chat.dto.ChatRoomHistoryResponse;
import com.teaching.backend.domain.chat.dto.MessageCreateRequest;
import com.teaching.backend.domain.chat.service.AskResult;
import com.teaching.backend.domain.chat.service.ChatMessageService;
import com.teaching.backend.domain.chat.service.ChatRoomHistoryResult;
import com.teaching.backend.global.response.ApiResponse;
import com.teaching.backend.global.security.entity.AuthMember;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 채팅방 내 메시지 히스토리 조회, 질문(Ask) 요청 처리를 담당하는 컨트롤러
@RestController
@RequiredArgsConstructor
@RequestMapping("/chatrooms/{chatRoomId}/messages")
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    @GetMapping
    public ApiResponse<ChatRoomHistoryResponse> getMessages(
            @AuthenticationPrincipal AuthMember authMember,
            @PathVariable Long chatRoomId
    ) {
        Long userId = authMember.getUserId();

        ChatRoomHistoryResult result = chatMessageService.getMessages(chatRoomId, userId);
        ChatRoomHistoryResponse response = MessageConverter.toHistoryResponse(result);

        return ApiResponse.onSuccess(response);
    }

    @PostMapping
    public ApiResponse<AskResponse> ask(
            @AuthenticationPrincipal AuthMember authMember,
            @PathVariable Long chatRoomId,
            @RequestBody MessageCreateRequest request
    ) {
        Long userId = authMember.getUserId();

        AskResult result = chatMessageService.ask(chatRoomId, userId, request);
        AskResponse response = MessageConverter.toAskResponse(result);

        return ApiResponse.onSuccess(response);
    }
}
