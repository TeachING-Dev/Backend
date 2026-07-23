package com.teaching.backend.domain.teachingmap.repository;

import com.teaching.backend.domain.teachingmap.entity.TeachingMap;
import com.teaching.backend.domain.teachingmap.entity.TeachingMapStep;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeachingMapStepRepository extends JpaRepository<TeachingMapStep, Long> {

}
