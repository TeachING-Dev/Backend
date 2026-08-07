package com.teaching.backend.domain.material.repository;

import com.teaching.backend.domain.material.entity.MaterialAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MaterialAnalysisRepository extends JpaRepository<MaterialAnalysis, Long> {

    Optional<MaterialAnalysis> findByMaterialId(Long materialId);

    @Query("""
            SELECT ma
            FROM MaterialAnalysis ma
            WHERE ma.material.id IN :materialIds
              AND ma.deletedAt IS NULL
            """)
    List<MaterialAnalysis> findAllActiveByMaterialIds(
            @Param("materialIds") List<Long> materialIds
    );

    List<MaterialAnalysis> findAllByMaterialIdIn(List<Long> materialIds);

    @Modifying
    @Query("UPDATE MaterialAnalysis m SET m.highlightStatus = 'GENERATING' " +
            "WHERE m.id = :id AND m.highlightStatus = 'NOT_STARTED'")
    int claimHighlightGeneration(@Param("id") Long id);

    @Modifying
    @Query("UPDATE MaterialAnalysis m SET m.highlightStatus = 'COMPLETED' WHERE m.id = :id")
    int completeHighlightGeneration(@Param("id") Long id);

    @Modifying
    @Query("UPDATE MaterialAnalysis m SET m.highlightStatus = 'NOT_STARTED' WHERE m.id = :id")
    int resetHighlightStatus(@Param("id") Long id);
}
