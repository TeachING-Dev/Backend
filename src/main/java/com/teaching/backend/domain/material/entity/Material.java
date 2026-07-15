package com.teaching.backend.domain.material.entity;

import com.teaching.backend.domain.folder.entity.Folder;
import com.teaching.backend.global.common.BaseSoftDeleteEntity;
import com.teaching.backend.domain.material.enums.AiStatus;
import com.teaching.backend.domain.material.enums.PlatformType;
import com.teaching.backend.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
@Table(name = "materials")
public class Material extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id", nullable = false)
    private Folder folder;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 500)
    private String originalUrl;

    @Column(length = 200)
    private String analysisTitle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiStatus aiStatus;

    private Integer difficulty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlatformType platformType;

    @Builder(access = AccessLevel.PRIVATE)
    private Material(User user, Folder folder, String title, String originalUrl,
                     PlatformType platformType) {
        this.user = user;
        this.folder = folder;
        this.title = title;
        this.originalUrl = originalUrl;
        this.platformType = platformType;
        this.aiStatus = AiStatus.PENDING;
    }

    public static Material create(User user, Folder folder, String title,
                                  String originalUrl, PlatformType platformType) {
        return Material.builder()
                .user(user)
                .folder(folder)
                .title(title)
                .originalUrl(originalUrl)
                .platformType(platformType)
                .build();
    }

    public void completeAnalysis(String analysisTitle, int difficulty) {
        this.analysisTitle = analysisTitle;
        this.difficulty = difficulty;
        this.aiStatus = AiStatus.COMPLETED;
    }

    public void failAnalysis() {
        this.aiStatus = AiStatus.FAILED;
    }
}
