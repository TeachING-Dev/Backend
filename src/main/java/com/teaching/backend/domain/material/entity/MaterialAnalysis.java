package com.teaching.backend.domain.material.entity;

import com.teaching.backend.global.common.BaseSoftDeleteEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "material_analysis")
public class MaterialAnalysis extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id", nullable = false, unique = true)
    private Material material;

    @Lob
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String summary;

    @Lob
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String detailAnalysis;

    @Column(nullable = false, length = 20)
    private String promptVersion;

    // 컬럼 추가 시 기존 행에 대한 백필이 필요하므로, DB 기본값을 명시해 ddl-auto가 안전하게 ALTER TABLE을 수행하도록 한다.
    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean isUserEdited;

    @Builder(access = AccessLevel.PRIVATE)
    private MaterialAnalysis(Material material, String summary, String detailAnalysis, String promptVersion) {
        this.material = material;
        this.summary = summary;
        this.detailAnalysis = detailAnalysis;
        this.promptVersion = promptVersion;
        this.isUserEdited = false;
    }

    public static MaterialAnalysis create(Material material, String summary,
                                          String detailAnalysis, String promptVersion) {
        return MaterialAnalysis.builder()
                .material(material)
                .summary(summary)
                .detailAnalysis(detailAnalysis)
                .promptVersion(promptVersion)
                .build();
    }

    public void updateAnalysis(String summary, String detailAnalysis, String promptVersion) {
        this.summary = summary;
        this.detailAnalysis = detailAnalysis;
        this.promptVersion = promptVersion;
    }

    public void editSummary(String summary) {
        this.summary = summary;
        this.isUserEdited = true;
    }
}
