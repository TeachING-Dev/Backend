package com.teaching.backend.domain.material.repository;

import com.teaching.backend.domain.material.entity.MaterialHighlight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MaterialHighlightRepository
        extends JpaRepository<MaterialHighlight, Long> {

    @Query("""
        SELECT h FROM MaterialHighlight h
        WHERE h.materialAnalysis.material.id = :materialId
        ORDER BY h.startPosition, h.id
    """)
    List<MaterialHighlight> findAllByMaterialId(@Param("materialId") Long materialId);
}
