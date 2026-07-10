package com.teaching.backend.domain;


import com.teaching.backend.domain.common.BaseSoftDeleteEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Getter
@NoArgsConstructor(access= AccessLevel.PROTECTED)
@AllArgsConstructor(access= AccessLevel.PROTECTED)
@Builder(access= AccessLevel.PRIVATE)
@SQLRestriction("deleted_at IS NULL")
//폴더명 중복 방지
@Table(
        name = "folders",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "name"})
)
public class Folder extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="user_id",nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String name;


    //파일 내 항목 개수
    @Column(nullable = false)
    @Builder.Default
    private Integer itemCount = 0;

    //생성 메서드
    public static Folder create(User user, String name) {
        return Folder.builder()
                .user(user)
                .name(name)
                .itemCount(0).build();
    }
}
