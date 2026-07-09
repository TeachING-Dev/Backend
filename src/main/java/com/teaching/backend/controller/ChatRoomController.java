package com.teaching.backend.controller;

import com.teaching.backend.converter.ChatRoomConverter;
import com.teaching.backend.dto.response.ChatRoomListResponse;
import com.teaching.backend.dto.response.ChatRoomResponse;
import com.teaching.backend.entity.ChatRoom;
import com.teaching.backend.global.auth.MockAuth;
import com.teaching.backend.global.response.ApiResponse;
import com.teaching.backend.global.response.SuccessCode;
import com.teaching.backend.service.ChatRoomPageResult;
import com.teaching.backend.service.ChatRoomService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/chatrooms")
public class ChatRoomController {

    private final ChatRoomService chatRoomService;

    @GetMapping
    public ResponseEntity<ApiResponse<ChatRoomListResponse>> getChatRooms(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) Integer size
    ) {
        Long userId = MockAuth.CURRENT_USER_ID;

        ChatRoomPageResult pageResult = chatRoomService.getChatRooms(userId, cursor, size);
        ChatRoomListResponse response = ChatRoomConverter.toListResponse(pageResult);

        return ResponseEntity.status(SuccessCode.CHATROOM_LIST_OK.getStatus())
                .body(ApiResponse.onSuccess(SuccessCode.CHATROOM_LIST_OK, response));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ChatRoomResponse>> createChatRoom() {
        Long userId = MockAuth.CURRENT_USER_ID;

        ChatRoom chatRoom = chatRoomService.createChatRoom(userId);
        ChatRoomResponse response = ChatRoomConverter.toResponse(chatRoom);

        return ResponseEntity.status(SuccessCode.CHATROOM_CREATED.getStatus())
                .body(ApiResponse.onSuccess(SuccessCode.CHATROOM_CREATED, response));
    }

    @DeleteMapping("/{chatRoomId}")
    public ApiResponse<Void> deleteChatRoom(@PathVariable Long chatRoomId) {
        Long userId = MockAuth.CURRENT_USER_ID;

        chatRoomService.deleteChatRoom(chatRoomId, userId);
        return ApiResponse.onSuccess(SuccessCode.CHATROOM_DELETED, null);
    }
}
