package com.teaching.backend.domain;

import com.teaching.backend.domain.common.BaseSoftDeleteEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    @Lob // 긴 텍스트 - varchar(255) 잘림 방지, TEXT 타입으로 매핑
    @Column(nullable = false)
    private String summary;

    @Lob
    @Column(nullable = false)
    private String detailAnalysis;

    @Column(nullable = false, length = 20)
    private String promptVersion;

    @Builder(access = AccessLevel.PRIVATE)
    private MaterialAnalysis(Material material, String summary, String detailAnalysis, String promptVersion) {
        this.material = material;
        this.summary = summary;
        this.detailAnalysis = detailAnalysis;
        this.promptVersion = promptVersion;
    }

    // 최초 분석 시 - 딱 1번만 호출됨
    public static MaterialAnalysis create(Material material, String summary,
                                          String detailAnalysis, String promptVersion) {
        return MaterialAnalysis.builder()
                .material(material)
                .summary(summary)
                .detailAnalysis(detailAnalysis)
                .promptVersion(promptVersion)
                .build();
    }

    // 재분석 시 - 기존 row를 덮어씀
    public void updateAnalysis(String summary, String detailAnalysis, String promptVersion) {
        this.summary = summary;
        this.detailAnalysis = detailAnalysis;
        this.promptVersion = promptVersion;
    }

}
