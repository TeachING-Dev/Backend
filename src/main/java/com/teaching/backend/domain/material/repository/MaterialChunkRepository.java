package com.teaching.backend.domain.material.repository;

import com.teaching.backend.domain.material.entity.MaterialChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaterialChunkRepository extends JpaRepository<MaterialChunk, Long> {

    List<MaterialChunk> findByIdIn(List<Long> ids);

    boolean existsByMaterialId(Long materialId);
}
