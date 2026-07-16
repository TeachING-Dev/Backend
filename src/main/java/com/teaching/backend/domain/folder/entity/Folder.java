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
@Table(
        name = "folders",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "name"})
)
public class Folder extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String name;

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
}
