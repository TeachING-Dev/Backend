package com.teaching.backend.domain;


import com.teaching.backend.domain.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access= AccessLevel.PROTECTED)
@AllArgsConstructor(access= AccessLevel.PROTECTED)
@Table(name="material_tag")
public class MaterialTag extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="material_id",nullable = false)
    private Material material;


    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="tag_id",nullable = false)
    private Tag tag;

    //대표 태그 여부

    @Column(nullable = false)
    private boolean isRepresentative;

    //객체 필드 채우기 용도 ( 외부 호출 불가 )
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

    // 자기 자신의 상태만 바꿈 - 다른 row는 모름
    public void markAsRepresentative() {
        this.isRepresentative = true;
    }

    public void unmarkAsRepresentative() {
        this.isRepresentative = false;
    }

}
