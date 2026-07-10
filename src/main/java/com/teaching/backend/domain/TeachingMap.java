package com.teaching.backend.domain;

import com.teaching.backend.domain.common.BaseSoftDeleteEntity;
import com.teaching.backend.domain.enums.TeachingMapStatus;
import com.teaching.backend.domain.enums.TeachingMapType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "teaching_maps")
public class TeachingMap extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id", nullable = false)
    private Folder folder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String title;

    @Lob
    private String description;

    @Column(nullable = false)
    private Integer currentSteps;

    @Column(nullable = false)
    private Integer totalSteps;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TeachingMapStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TeachingMapType type;

    @Column(nullable = false)
    private Boolean isDraft;

    @Builder(access = AccessLevel.PRIVATE)
    private TeachingMap(Folder folder, User user, String title, String description,
                        Integer totalSteps, TeachingMapType type, Boolean isDraft) {
        this.folder = folder;
        this.user = user;
        this.title = title;
        this.description = description;
        this.currentSteps = 0; // 생성 시점엔 항상 0단계
        this.totalSteps = totalSteps;
        this.status = TeachingMapStatus.IN_PROGRESS; // 생성 시점엔 항상 진행중
        this.type = type;
        this.isDraft = isDraft;
    }

    public static TeachingMap create(Folder folder, User user, String title, String description,
                                     Integer totalSteps, TeachingMapType type, Boolean isDraft) {
        return TeachingMap.builder()
                .folder(folder)
                .user(user)
                .title(title)
                .description(description)
                .totalSteps(totalSteps)
                .type(type)
                .isDraft(isDraft)
                .build();
    }

    // 학습 진행 - 한 단계 완료
    public void advanceStep() {
        if (this.currentSteps >= this.totalSteps) {
            throw new IllegalStateException("이미 마지막 단계입니다.");
        }
        this.currentSteps++;
        if (this.currentSteps.equals(this.totalSteps)) {
            this.status = TeachingMapStatus.FINISHED;
        }
    }

    // 임시저장 -> 정식 저장 전환
    public void finalizeDraft() {
        this.isDraft = false;
    }
}