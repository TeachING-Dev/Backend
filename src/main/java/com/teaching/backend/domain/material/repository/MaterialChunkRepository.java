package com.teaching.backend.domain.material.repository;

import com.teaching.backend.domain.material.entity.MaterialChunk;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialChunkRepository
        extends JpaRepository<MaterialChunk, Long> {
}