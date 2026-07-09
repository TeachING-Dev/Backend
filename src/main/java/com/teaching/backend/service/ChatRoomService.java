package com.teaching.backend.service;

import com.teaching.backend.entity.ChatRoom;
import com.teaching.backend.global.exception.ErrorCode;
import com.teaching.backend.global.exception.GeneralException;
import com.teaching.backend.repository.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;

    public ChatRoomPageResult getChatRooms(Long userId, Long cursor, int size) {
        List<ChatRoom> chatRooms = chatRoomRepository.findByUserId(userId);
        chatRooms.sort(
                Comparator.comparing(ChatRoom::getLastMessageAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ChatRoom::getCreatedAt, Comparator.reverseOrder())
        );

        int startIndex = 0;
        if (cursor != null) {
            int cursorIndex = indexOf(chatRooms, cursor);
            if (cursorIndex == -1) {
                throw new GeneralException(ErrorCode.CHATROOM_NOT_FOUND);
            }
            startIndex = cursorIndex + 1;
        }

        if (startIndex >= chatRooms.size()) {
            return new ChatRoomPageResult(List.of(), null);
        }

        int endIndex = Math.min(startIndex + size, chatRooms.size());
        List<ChatRoom> page = chatRooms.subList(startIndex, endIndex);
        Long nextCursor = (endIndex < chatRooms.size()) ? page.get(page.size() - 1).getId() : null;

        return new ChatRoomPageResult(page, nextCursor);
    }

    private int indexOf(List<ChatRoom> chatRooms, Long chatRoomId) {
        for (int i = 0; i < chatRooms.size(); i++) {
            if (chatRooms.get(i).getId().equals(chatRoomId)) {
                return i;
            }
        }
        return -1;
    }

    @Transactional
    public ChatRoom createChatRoom(Long userId) {
        // TODO: 무료 회원 대화방 개수 제한(CHATROOM_LIMIT_EXCEEDED) 정책 확정 후 적용
        ChatRoom chatRoom = ChatRoom.builder()
                .userId(userId)
                .title(null)
                .build();

        return chatRoomRepository.save(chatRoom);
    }

    public ChatRoom getChatRoom(Long chatRoomId, Long userId) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new GeneralException(ErrorCode.CHATROOM_NOT_FOUND));

        if (!chatRoom.getUserId().equals(userId)) {
            throw new GeneralException(ErrorCode.AUTH_FORBIDDEN);
        }

        return chatRoom;
    }

    @Transactional
    public void deleteChatRoom(Long chatRoomId, Long userId) {
        ChatRoom chatRoom = getChatRoom(chatRoomId, userId);
        chatRoomRepository.delete(chatRoom);
    }
}
