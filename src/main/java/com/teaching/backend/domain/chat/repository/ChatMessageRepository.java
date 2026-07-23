package com.teaching.backend.domain.chat.repository;

import com.teaching.backend.domain.chat.entity.ChatMessage;
import com.teaching.backend.domain.chat.enums.ChatRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

// ChatMessage 엔티티에 대한 JPA 레포지토리
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByChatRoomIdOrderByCreatedAtAsc(Long chatRoomId);

    // 무료 회원 일일 질문 횟수 제한 검증용: 특정 유저가 기준 시각 이후 보낸 유저 메시지 수
    long countByChatRoom_User_IdAndRoleAndCreatedAtGreaterThanEqual(Long userId, ChatRole role, LocalDateTime createdAt);
}

