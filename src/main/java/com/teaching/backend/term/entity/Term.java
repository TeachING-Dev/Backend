package com.teaching.backend.term.entity;

import com.teaching.backend.global.common.BaseSoftDeleteEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "term",
        uniqueConstraints = @UniqueConstraint(name = "uk_term_title_version", columnNames = {"title", "version"})
)
public class Term extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private Boolean isRequired;

    @Column(nullable = false)
    private String version;

    @Builder(access = AccessLevel.PRIVATE)
    private Term(String title, String content, Boolean isRequired, String version) {
        this.title = title;
        this.content = content;
        this.isRequired = isRequired;
        this.version = version;
    }

    public static Term create(String title, String content, Boolean isRequired, String version) {
        return Term.builder()
                .title(title)
                .content(content)
                .isRequired(isRequired)
                .version(version)
                .build();
    }
}
