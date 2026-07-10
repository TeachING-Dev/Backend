package com.teaching.backend.domain;

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
@Table(name = "material_chunks")
public class MaterialChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @Column(nullable = false)
    private Integer chunkIndex;

    @Lob
    @Column(nullable = false)
    private String chunkText;

    // Qdrant에 저장된 벡터 포인트의 식별자 (UUID 문자열 or 숫자 - Qdrant 설정에 따름)
    @Column(nullable = false, unique = true, length = 100)
    private String qdrantPointId;

    @Column(length = 100)
    private String position;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder(access = AccessLevel.PRIVATE)
    private MaterialChunk(Material material, Integer chunkIndex, String chunkText,
                          String qdrantPointId, String position) {
        this.material = material;
        this.chunkIndex = chunkIndex;
        this.chunkText = chunkText;
        this.qdrantPointId = qdrantPointId;
        this.position = position;
    }

    public static MaterialChunk create(Material material, Integer chunkIndex, String chunkText,
                                       String qdrantPointId, String position) {
        return MaterialChunk.builder()
                .material(material)
                .chunkIndex(chunkIndex)
                .chunkText(chunkText)
                .qdrantPointId(qdrantPointId)
                .position(position)
                .build();
    }
}