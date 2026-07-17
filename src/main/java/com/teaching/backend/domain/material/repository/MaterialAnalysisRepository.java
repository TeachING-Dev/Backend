package com.teaching.backend.domain.material.repository;

import com.teaching.backend.domain.material.entity.MaterialAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// MaterialAnalysis 엔티티에 대한 JPA 레포지토리
public interface MaterialAnalysisRepository extends JpaRepository<MaterialAnalysis, Long> {

    Optional<MaterialAnalysis> findByMaterialId(Long materialId);
}
