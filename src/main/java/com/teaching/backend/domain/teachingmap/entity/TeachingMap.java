package com.teaching.backend.domain.teachingmap.entity;

import com.teaching.backend.global.common.BaseSoftDeleteEntity;
import com.teaching.backend.domain.folder.entity.Folder;
import com.teaching.backend.domain.teachingmap.enums.TeachingMapStatus;
import com.teaching.backend.domain.teachingmap.enums.TeachingMapType;
import com.teaching.backend.domain.user.entity.User;
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
        this.currentSteps = 0;
        this.totalSteps = totalSteps;
        this.status = TeachingMapStatus.IN_PROGRESS;
        this.type = type;
        this.isDraft = isDraft;
    }

    public static TeachingMap create(Folder folder, User user, String title, String description,
                                     Integer totalSteps, TeachingMapType type, Boolean isDraft) {
        if (totalSteps == null || totalSteps <= 0) {
                       throw new IllegalArgumentException("totalSteps는 1 이상이어야 합니다.");
                   }

    if (type == TeachingMapType.ALL) {
        throw new IllegalArgumentException("ALL은 티칭맵 생성 시 사용할 수 없습니다.");
    }
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

    public void advanceStep() {
        if (this.currentSteps >= this.totalSteps) {
            throw new IllegalStateException("이미 마지막 단계입니다.");
        }
        this.currentSteps++;
        if (this.currentSteps.equals(this.totalSteps)) {
            this.status = TeachingMapStatus.FINISHED;
        }
    }

    public void finalizeDraft() {
        this.isDraft = false;
    }
}
