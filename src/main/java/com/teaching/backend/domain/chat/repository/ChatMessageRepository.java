package com.teaching.backend.domain.chat.repository;

import com.teaching.backend.domain.chat.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// ChatMessage 엔티티에 대한 JPA 레포지토리
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByChatRoomIdOrderByCreatedAtAsc(Long chatRoomId);
}

