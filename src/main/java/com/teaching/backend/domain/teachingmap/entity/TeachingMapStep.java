package com.teaching.backend.domain.teachingmap.entity;

import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.global.common.BaseSoftDeleteEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "teaching_map_steps")
public class TeachingMapStep extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "teaching_map_step_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teaching_map_id", nullable = false)
    private TeachingMap teachingMap;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    @Column(name = "step_title", nullable = false, length = 100)
    private String stepTitle;

    @Column(name = "is_finished", nullable = false)
    private Boolean isFinished;

    @Lob
    @Column(name = "tip")
    private String tip;

    @Builder(access = AccessLevel.PRIVATE)
    private TeachingMapStep(TeachingMap teachingMap, Material material, Integer stepOrder,
                            String stepTitle, String tip) {
        this.teachingMap = teachingMap;
        this.material = material;
        this.stepOrder = stepOrder;
        this.stepTitle = stepTitle;
        this.tip = tip;
        this.isFinished = false;
    }

    public static TeachingMapStep create(TeachingMap teachingMap, Material material, Integer stepOrder,
                                         String stepTitle, String tip) {
        return TeachingMapStep.builder()
                .teachingMap(teachingMap)
                .material(material)
                .stepOrder(stepOrder)
                .stepTitle(stepTitle)
                .tip(tip)
                .build();
    }

    public void finish() {
        if (this.isFinished) {
            throw new IllegalStateException("이미 완료된 스텝입니다.");
        }
        this.isFinished = true;
    }
}