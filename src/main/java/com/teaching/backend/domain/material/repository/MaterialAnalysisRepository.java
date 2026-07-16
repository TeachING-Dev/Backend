package com.teaching.backend.domain.material.repository;

import com.teaching.backend.domain.material.entity.MaterialAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialAnalysisRepository
        extends JpaRepository<MaterialAnalysis, Long> {
}