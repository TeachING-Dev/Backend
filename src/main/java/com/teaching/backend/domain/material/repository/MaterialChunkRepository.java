package com.teaching.backend.domain.material.repository;

import com.teaching.backend.domain.material.entity.MaterialChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// MaterialChunk 엔티티에 대한 JPA 레포지토리
public interface MaterialChunkRepository extends JpaRepository<MaterialChunk, Long> {

    List<MaterialChunk> findByIdIn(List<Long> ids);

    boolean existsByMaterialId(Long materialId);
}
