package com.teaching.backend.domain.material.repository;

import com.teaching.backend.domain.material.entity.MaterialHighlight;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialHighlightRepository
        extends JpaRepository<MaterialHighlight, Long> {
}
