package com.teaching.backend.domain.material.entity;

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
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String chunkText;

    @Column(nullable = false, unique = true, length = 100)
    private String qdrantPointId;

    private Integer startLine;

    private Integer endLine;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder(access = AccessLevel.PRIVATE)
    private MaterialChunk(Material material, Integer chunkIndex, String chunkText,
                          String qdrantPointId, Integer startLine, Integer endLine) {
        this.material = material;
        this.chunkIndex = chunkIndex;
        this.chunkText = chunkText;
        this.qdrantPointId = qdrantPointId;
        this.startLine = startLine;
        this.endLine = endLine;
    }

    public static MaterialChunk create(Material material, Integer chunkIndex, String chunkText,
                                       String qdrantPointId, Integer startLine, Integer endLine) {
        return MaterialChunk.builder()
                .material(material)
                .chunkIndex(chunkIndex)
                .chunkText(chunkText)
                .qdrantPointId(qdrantPointId)
                .startLine(startLine)
                .endLine(endLine)
                .build();
    }

    public void updateContent(String chunkText, String qdrantPointId, Integer startLine, Integer endLine) {
        this.chunkText = chunkText;
        this.qdrantPointId = qdrantPointId;
        this.startLine = startLine;
        this.endLine = endLine;
    }

    // 원본 문서 내 위치를 사람이 읽을 수 있는 형태로 표시 ("15행" 또는 "15-18행")
    // 재색인 전 레거시 청크는 라인 정보가 없으므로 청크 순번으로 대체 표시
    public String displayPosition() {
        if (startLine == null || endLine == null) {
            return "청크 " + chunkIndex;
        }
        return startLine.equals(endLine) ? startLine + "행" : startLine + "-" + endLine + "행";
    }
}
