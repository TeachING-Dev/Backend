package com.teaching.backend.domain.material.repository;

import com.teaching.backend.domain.material.entity.Material;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialRepository extends JpaRepository<Material, Long> {
}