package com.teaching.backend.domain.tag.repository;

import com.teaching.backend.domain.tag.entity.MaterialTag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialTagRepository
        extends JpaRepository<MaterialTag, Long> {
}