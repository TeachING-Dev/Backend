package com.teaching.backend.domain.tag.repository;

import com.teaching.backend.domain.tag.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Long> {

    Optional<Tag> findByName(String name);

    @Modifying
    @Query(
            value = """
                    INSERT INTO tag (name)
                    VALUES (:name)
                    ON DUPLICATE KEY UPDATE name = name
                    """,
            nativeQuery = true
    )
    int insertIfAbsent(@Param("name") String name);
}
