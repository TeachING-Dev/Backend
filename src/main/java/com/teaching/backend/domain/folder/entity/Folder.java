package com.teaching.backend.domain.folder.entity;

import com.teaching.backend.global.common.BaseSoftDeleteEntity;
import com.teaching.backend.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder(access = AccessLevel.PRIVATE)
@SQLRestriction("deleted_at IS NULL")
@Table(name = "folders")
public class Folder extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String name;

    /**
     * DB 컬럼(item_count)이 NOT NULL이고 기본값이 없어 필드 자체는 유지해야 하지만,
     * 값은 어디에서도 갱신되지 않아 항상 0으로 고정된 죽은 필드다 — 읽지 말 것
     * (휴지통 폴더의 자료 개수는 TrashService 에서 native COUNT 쿼리로 별도 계산한다).
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer itemCount = 0;

    public static Folder create(User user, String name) {
        return Folder.builder()
                .user(user)
                .name(name)
                .itemCount(0)
                .build();
    }

    public void rename(String name) {
        this.name = name;
    }
}
