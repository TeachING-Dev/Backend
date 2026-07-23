package com.teaching.backend.domain.chat.repository;

import com.teaching.backend.domain.chat.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// ChatRoom 엔티티에 대한 JPA 레포지토리 (소프트 삭제된 대화방은 조회에서 제외)
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    List<ChatRoom> findByUserIdAndDeletedAtIsNull(Long userId);

    Optional<ChatRoom> findByIdAndDeletedAtIsNull(Long id);

    long countByUserIdAndDeletedAtIsNull(Long userId);
}
