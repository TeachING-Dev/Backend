package com.teaching.backend.domain.tag.repository;

import com.teaching.backend.domain.tag.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, Long> {
}