package com.teaching.backend.domain.tag.entity;

import com.teaching.backend.global.common.BaseTimeEntity;
import com.teaching.backend.domain.material.entity.Material;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "material_tag")
public class MaterialTag extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id", nullable = false)
    private Tag tag;

    @Column(nullable = false)
    private boolean isRepresentative;

    @Builder(access = AccessLevel.PRIVATE)
    private MaterialTag(Material material, Tag tag, boolean isRepresentative) {
        this.material = material;
        this.tag = tag;
        this.isRepresentative = isRepresentative;
    }

    public static MaterialTag create(Material material, Tag tag) {
        return MaterialTag.builder()
                .material(material)
                .tag(tag)
                .isRepresentative(false)
                .build();
    }

    public void markAsRepresentative() {
        this.isRepresentative = true;
    }

    public void unmarkAsRepresentative() {
        this.isRepresentative = false;
    }
}
