package com.teaching.backend.material.entity;

import com.teaching.backend.material.enums.AiGuideContentType;
import com.teaching.backend.material.enums.GuideType;
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
@Table(name = "ai_guides")
public class AiGuide {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_highlight_id", nullable = false)
    private MaterialHighlight materialHighlight;

    @Column(nullable = false, length = 50)
    private String promptVer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GuideType guideType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiGuideContentType type;

    @Column(nullable = false, length = 100)
    private String title;

    @Lob
    @Column(nullable = false)
    private String content;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder(access = AccessLevel.PRIVATE)
    private AiGuide(MaterialHighlight materialHighlight, String promptVer, GuideType guideType,
                    AiGuideContentType type, String title, String content) {
        this.materialHighlight = materialHighlight;
        this.promptVer = promptVer;
        this.guideType = guideType;
        this.type = type;
        this.title = title;
        this.content = content;
    }

    public static AiGuide create(MaterialHighlight materialHighlight, String promptVer, GuideType guideType,
                                 AiGuideContentType type, String title, String content) {
        return AiGuide.builder()
                .materialHighlight(materialHighlight)
                .promptVer(promptVer)
                .guideType(guideType)
                .type(type)
                .title(title)
                .content(content)
                .build();
    }
}
