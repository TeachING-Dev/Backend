package com.teaching.backend.domain.chat.repository;

import com.teaching.backend.domain.chat.entity.ChatSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

// ChatSource 엔티티에 대한 JPA 레포지토리
public interface ChatSourceRepository extends JpaRepository<ChatSource, Long> {

    @Query("""
            SELECT cs FROM ChatSource cs
            JOIN FETCH cs.materialChunk mc
            JOIN FETCH mc.material m
            JOIN FETCH m.folder f
            WHERE cs.chatMessage.id IN :chatMessageIds
            ORDER BY cs.createdAt ASC
            """)
    List<ChatSource> findAllByChatMessageIdInWithMaterial(@Param("chatMessageIds") List<Long> chatMessageIds);
}
