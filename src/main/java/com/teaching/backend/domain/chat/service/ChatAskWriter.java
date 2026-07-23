package com.teaching.backend.domain.chat.service;

import com.teaching.backend.domain.chat.entity.ChatMessage;
import com.teaching.backend.domain.chat.entity.ChatRoom;
import com.teaching.backend.domain.chat.entity.ChatSource;
import com.teaching.backend.domain.chat.enums.ChatRole;
import com.teaching.backend.domain.chat.exception.ChatErrorCode;
import com.teaching.backend.domain.chat.exception.ChatException;
import com.teaching.backend.domain.chat.repository.ChatMessageRepository;
import com.teaching.backend.domain.chat.repository.ChatSourceRepository;
import com.teaching.backend.domain.material.entity.MaterialChunk;
import com.teaching.backend.domain.user.entity.User;
import com.teaching.backend.domain.user.enums.MembershipType;
import com.teaching.backend.domain.user.repository.UserRepository;
import com.teaching.backend.global.apiPayload.code.GlobalErrorCode;
import com.teaching.backend.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

// ask()의 외부 호출(벡터 검색, LLM 답변 생성) 전후로 짧은 트랜잭션 안에서 quota 예약/저장을 수행하는 전용 컴포넌트.
// ChatMessageService의 메서드로 두면 self-invocation 때문에 @Transactional이 적용되지 않아 별도 빈으로 분리함.
@Component
@RequiredArgsConstructor
class ChatAskWriter {

    private final ChatRoomService chatRoomService;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatSourceRepository chatSourceRepository;
    private final UserRepository userRepository;

    // 외부 호출(검색/LLM) 전에 유저 행을 잠그고 quota를 원자적으로 예약한다.
    // 유저 메시지를 곧바로 저장해 예약 상태로 삼으므로, 이 시점 이후의 동시 요청은 늘어난 카운트를 보고 즉시 거절된다.
    @Transactional
    Reservation reserve(Long chatRoomId, Long userId, String content) {
        ChatRoom chatRoom = chatRoomService.getChatRoom(chatRoomId, userId);

        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new GeneralException(GlobalErrorCode.NOT_FOUND));

        if (user.getMembershipType() == MembershipType.FREE
                && countTodayUserMessages(userId) >= ChatMessageService.FREE_DAILY_QUESTION_LIMIT) {
            throw new ChatException(ChatErrorCode.DAILY_QUESTION_LIMIT_EXCEEDED);
        }

        ChatMessage userMessage = chatMessageRepository.save(ChatMessage.createUserMessage(chatRoom, content));

        return new Reservation(chatRoomId, userId, userMessage);
    }

    // 외부 호출이 실패한 경우 예약해둔 유저 메시지를 삭제해 quota를 반환한다.
    @Transactional
    void release(Reservation reservation) {
        chatMessageRepository.deleteById(reservation.userMessage().getId());
    }

    // 외부 호출(검색/LLM) 이후 결과를 짧은 트랜잭션 안에서 저장한다.
    @Transactional
    AskResult finalizeAnswer(Reservation reservation, String answer, boolean isFallback, List<MaterialChunk> relevantChunks) {
        // 외부 호출 도중 방이 삭제되는 경우를 대비해 쓰기 시점에 다시 조회
        ChatRoom chatRoom = chatRoomService.getChatRoom(reservation.chatRoomId(), reservation.userId());

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

        // 프리미엄 회원은 횟수 제한이 없으므로 무제한(null)으로 응답
        Integer remainingCount = null;
        if (chatRoom.getUser().getMembershipType() == MembershipType.FREE) {
            long todayCount = countTodayUserMessages(reservation.userId());
            remainingCount = Math.max(0, ChatMessageService.FREE_DAILY_QUESTION_LIMIT - (int) todayCount);
        }

        return new AskResult(chatRoom, reservation.userMessage(), aiMessage, aiMessageSources, remainingCount);
    }

    private long countTodayUserMessages(Long userId) {
        return chatMessageRepository.countByChatRoom_User_IdAndRoleAndCreatedAtGreaterThanEqual(
                userId, ChatRole.USER, LocalDate.now(ChatMessageService.KST).atStartOfDay());
    }

    // ChatSource.citedAt은 NOT NULL이라, MaterialChunk에 위치 정보(position)가 아직 없는 경우 대체 값을 채워줌
    private String citedAtOf(MaterialChunk chunk) {
        return chunk.getPosition() != null ? chunk.getPosition() : ("청크 " + chunk.getChunkIndex());
    }

    record Reservation(Long chatRoomId, Long userId, ChatMessage userMessage) {
    }
}
