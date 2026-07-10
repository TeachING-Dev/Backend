package com.teaching.backend.material.entity;

import com.teaching.backend.material.enums.HighlightType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "material_highlights")
public class MaterialHighlight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_chunk_id", nullable = false)
    private MaterialChunk materialChunk;

    @Lob
    @Column(nullable = false)
    private String highlightText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HighlightType highlightType;

    @Column(nullable = false)
    private Integer startPosition;

    @Column(nullable = false)
    private Integer endPosition;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder(access = AccessLevel.PRIVATE)
    private MaterialHighlight(MaterialChunk materialChunk, String highlightText, HighlightType highlightType,
                              Integer startPosition, Integer endPosition) {
        if (startPosition >= endPosition) {
            throw new IllegalArgumentException("시작 위치는 종료 위치보다 작아야 합니다.");
        }
        this.materialChunk = materialChunk;
        this.highlightText = highlightText;
        this.highlightType = highlightType;
        this.startPosition = startPosition;
        this.endPosition = endPosition;
    }

    public static MaterialHighlight create(MaterialChunk materialChunk, String highlightText,
                                           HighlightType highlightType, Integer startPosition, Integer endPosition) {
        return MaterialHighlight.builder()
                .materialChunk(materialChunk)
                .highlightText(highlightText)
                .highlightType(highlightType)
                .startPosition(startPosition)
                .endPosition(endPosition)
                .build();
    }
}
