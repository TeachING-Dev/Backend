package com.teaching.backend.domain;

import com.teaching.backend.domain.common.BaseSoftDeleteEntity;
import com.teaching.backend.domain.enums.AiStatus;
import com.teaching.backend.domain.enums.PlatformType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Getter
@NoArgsConstructor(access= AccessLevel.PROTECTED)
@AllArgsConstructor(access= AccessLevel.PROTECTED)

//폴더명 중복 방지
@Table(
        name = "materials")
public class Material extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="user_id",nullable = false)
    private User user;


    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="folder_id",nullable = false)
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

    //난이도
    private Integer difficulty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlatformType platformType;

    @Builder(access= AccessLevel.PRIVATE)
    private Material(User user, Folder folder, String title, String originalUrl,
                     PlatformType platformType) {
        this.user = user;
        this.folder = folder;
        this.title = title;
        this.originalUrl = originalUrl;
        this.platformType = platformType;
        this.aiStatus = AiStatus.PENDING; // 생성 시점 기본값
    }

    @Builder(access = AccessLevel.PRIVATE)
    //ai 분석 전에 채워져야 하는 값
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

    // AI 분석 완료 시 상태 변경용 비즈니스 메서드
    public void completeAnalysis(String analysisTitle, int difficulty) {
        this.analysisTitle = analysisTitle;
        this.difficulty = difficulty;
        this.aiStatus = AiStatus.COMPLETED;
    }

    public void failAnalysis() {
        this.aiStatus = AiStatus.FAILED;
    }
}
