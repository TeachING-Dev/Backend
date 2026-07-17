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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 채팅방 내 메시지 히스토리 조회, 질문(Ask) 요청 처리를 담당하는 컨트롤러
@Tag(name = "ChatMessage", description = "채팅 메시지 조회 및 질문(RAG) API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/chatrooms/{chatRoomId}/messages")
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    @Operation(
            summary = "메시지 히스토리 조회",
            description = "채팅방에 쌓인 유저 질문/AI 답변 전체 히스토리를 시간순으로 조회합니다. AI 답변에는 인용된 자료 출처가 함께 포함됩니다."
    )
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

    @Operation(
            summary = "질문하기 (RAG)",
            description = "질문을 벡터 검색으로 관련 자료를 찾아 근거로 삼아 AI 답변을 생성합니다. 관련 자료가 없으면 일반 지식 기반 답변(fallback)으로 응답합니다."
    )
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
