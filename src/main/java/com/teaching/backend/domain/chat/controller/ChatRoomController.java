package com.teaching.backend.domain.chat.controller;

import com.teaching.backend.domain.chat.converter.ChatRoomConverter;
import com.teaching.backend.domain.chat.dto.ChatRoomListResponse;
import com.teaching.backend.domain.chat.dto.ChatRoomResponse;
import com.teaching.backend.domain.chat.dto.MessageCreateRequest;
import com.teaching.backend.domain.chat.entity.ChatRoom;
import com.teaching.backend.domain.chat.service.ChatRoomPageResult;
import com.teaching.backend.domain.chat.service.ChatRoomService;
import com.teaching.backend.global.response.ApiResponse;
import com.teaching.backend.global.security.entity.AuthMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 채팅방 생성, 목록 조회(커서 페이지네이션), 삭제를 처리하는 컨트롤러
@Tag(name = "ChatRoom", description = "채팅방 생성/조회/삭제 API")
@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/chatrooms")
public class ChatRoomController {

    private final ChatRoomService chatRoomService;

    @Operation(
            summary = "채팅방 목록 조회",
            description = "로그인한 사용자의 채팅방 목록을 커서 기반 페이지네이션으로 조회합니다. 최근 메시지가 온 순서대로 정렬됩니다."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<ChatRoomListResponse>> getChatRooms(
            @AuthenticationPrincipal AuthMember authMember,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) Integer size
    ) {
        Long userId = authMember.getUserId();

        ChatRoomPageResult pageResult = chatRoomService.getChatRooms(userId, cursor, size);
        ChatRoomListResponse response = ChatRoomConverter.toListResponse(pageResult);

        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }

    @Operation(
            summary = "채팅방 생성",
            description = "첫 질문 내용을 받아 새 채팅방을 생성합니다. 채팅방 제목은 질문 내용으로 자동 생성됩니다."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<ChatRoomResponse>> createChatRoom(
            @AuthenticationPrincipal AuthMember authMember,
            @RequestBody MessageCreateRequest request
    ) {
        Long userId = authMember.getUserId();

        ChatRoom chatRoom = chatRoomService.createChatRoom(userId, request.content());
        ChatRoomResponse response = ChatRoomConverter.toResponse(chatRoom);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.onSuccess(response));
    }

    @Operation(
            summary = "채팅방 삭제",
            description = "본인 소유 채팅방을 소프트 삭제합니다."
    )
    @DeleteMapping("/{chatRoomId}")
    public ApiResponse<Void> deleteChatRoom(
            @AuthenticationPrincipal AuthMember authMember,
            @PathVariable Long chatRoomId
    ) {
        Long userId = authMember.getUserId();

        chatRoomService.deleteChatRoom(chatRoomId, userId);
        return ApiResponse.onSuccess(null);
    }
}
