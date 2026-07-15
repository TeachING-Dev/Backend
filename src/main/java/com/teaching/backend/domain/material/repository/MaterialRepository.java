package com.teaching.backend.domain.material.repository;

import com.teaching.backend.domain.material.entity.Material;
import org.springframework.data.jpa.repository.JpaRepository;

// Material 엔티티에 대한 JPA 레포지토리
public interface MaterialRepository extends JpaRepository<Material, Long> {
}
