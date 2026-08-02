package com.teaching.backend.domain.material.repository;

import com.teaching.backend.domain.material.entity.MaterialChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MaterialChunkRepository
        extends JpaRepository<MaterialChunk, Long> {

    List<MaterialChunk> findByIdIn(List<Long> ids);

    List<MaterialChunk> findAllByMaterial_IdOrderByChunkIndexAsc(Long materialId);

    List<MaterialChunk> findAllByMaterial_IdInOrderByMaterial_IdAscChunkIndexAsc(List<Long> materialIds);

    boolean existsByMaterialId(Long materialId);

    /** 질문에서 뽑아낸 키워드 하나가 이 사용자의 청크 본문 안에 문자 그대로 있는지 찾는다.
     *  MaterialRepository.findIdsMentionedInQuestion과 반대 방향(자료명이 질문에 있는지가 아니라,
     *  질문의 단어가 본문에 있는지)의 LOCATE 매칭이다. */
    @Query("""
            SELECT c FROM MaterialChunk c
            JOIN c.material m
            WHERE m.user.id = :userId
              AND LOCATE(LOWER(:keyword), LOWER(CAST(c.chunkText AS string))) > 0
            ORDER BY m.id ASC, c.chunkIndex ASC
            """)
    List<MaterialChunk> findByUserIdAndChunkTextContaining(
            @Param("userId") Long userId,
            @Param("keyword") String keyword
    );
}
