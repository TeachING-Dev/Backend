package com.teaching.backend.domain.tag.repository;

import com.teaching.backend.domain.tag.entity.MaterialTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MaterialTagRepository
        extends JpaRepository<MaterialTag, Long> {

    @Query("""
            SELECT mt
            FROM MaterialTag mt
            JOIN FETCH mt.material m
            JOIN FETCH m.user
            WHERE mt.id = :materialTagId
            """)
    Optional<MaterialTag> findByIdWithMaterialAndUser(
            @Param("materialTagId") Long materialTagId
    );

    @Query("""
            SELECT mt
            FROM MaterialTag mt
            JOIN FETCH mt.tag
            WHERE mt.material.id IN :materialIds
            ORDER BY mt.material.id ASC, mt.createdAt ASC, mt.id ASC
            """)
    List<MaterialTag> findAllWithTagByMaterialIds(
            @Param("materialIds") List<Long> materialIds
    );
}
