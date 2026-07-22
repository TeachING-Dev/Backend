package com.teaching.backend.domain.chat.service;

import com.teaching.backend.domain.chat.entity.ChatRoom;
import com.teaching.backend.domain.chat.repository.ChatRoomRepository;
import com.teaching.backend.global.apiPayload.code.GlobalErrorCode;
import com.teaching.backend.global.exception.GeneralException;
import com.teaching.backend.domain.user.entity.User;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

// 채팅방 조회/생성/삭제 및 커서 기반 페이지네이션 로직을 담당하는 서비스
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatRoomService {

    private static final int TITLE_MAX_LENGTH = 15;
    private static final String TITLE_ELLIPSIS = "...";

    private final ChatRoomRepository chatRoomRepository;
    private final EntityManager entityManager;

    public ChatRoomPageResult getChatRooms(Long userId, Long cursor, int size) {
        List<ChatRoom> chatRooms = chatRoomRepository.findByUserIdAndDeletedAtIsNull(userId);
        chatRooms.sort(
                Comparator.comparing(ChatRoom::getLastMessageAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ChatRoom::getCreatedAt, Comparator.reverseOrder())
        );

        int startIndex = 0;
        if (cursor != null) {
            int cursorIndex = indexOf(chatRooms, cursor);
            if (cursorIndex == -1) {
                throw new GeneralException(GlobalErrorCode.NOT_FOUND);
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
    public ChatRoom createChatRoom(Long userId, String content) {
        // TODO: 무료 회원 대화방 개수 제한(CHATROOM_LIMIT_EXCEEDED) 정책 확정 후 적용
        // TODO: User 도메인 구축 완료 후 UserRepository 조회로 교체
        User user = entityManager.getReference(User.class, userId);
        ChatRoom chatRoom = ChatRoom.create(user, generateTitle(content));

        return chatRoomRepository.save(chatRoom);
    }

    private String generateTitle(String content) {
        if (content.codePointCount(0, content.length()) <= TITLE_MAX_LENGTH) {
            return content;
        }

        int cutIndex = content.offsetByCodePoints(0, TITLE_MAX_LENGTH - TITLE_ELLIPSIS.length());
        return content.substring(0, cutIndex) + TITLE_ELLIPSIS;
    }

    public ChatRoom getChatRoom(Long chatRoomId, Long userId) {
        ChatRoom chatRoom = chatRoomRepository.findByIdAndDeletedAtIsNull(chatRoomId)
                .orElseThrow(() -> new GeneralException(GlobalErrorCode.NOT_FOUND));

        if (!chatRoom.getUser().getId().equals(userId)) {
            throw new GeneralException(GlobalErrorCode.FORBIDDEN);
        }

        return chatRoom;
    }

    @Transactional
    public void deleteChatRoom(Long chatRoomId, Long userId) {
        ChatRoom chatRoom = getChatRoom(chatRoomId, userId);
        chatRoom.delete();
    }
}
