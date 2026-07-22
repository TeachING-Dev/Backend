package com.teaching.backend.domain.user.entity;

import com.teaching.backend.global.common.BaseSoftDeleteEntity;
import com.teaching.backend.domain.user.enums.Gender;
import com.teaching.backend.domain.user.enums.TeacherPersona;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
@Table(name = "users")
public class User extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Email
    @Column(nullable = false)
    private String email;

    // 활성 사용자 간 닉네임 유일성은 DB의 생성 컬럼(active_nickname) + unique 인덱스로 보장한다.
    // (soft-delete 된 사용자는 닉네임을 자유롭게 반납 - 아래 마이그레이션 SQL 참고)
    @Column(nullable = false)
    private String nickname;


    private LocalDate birthday;

    @Enumerated(EnumType.STRING)

    private Gender gender;

    private String profileImageUrl;

    @Column(nullable = false)
    private Boolean notificationsEnabled;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TeacherPersona teacherPersona;

    @Builder(access = AccessLevel.PRIVATE)
    private User(String email, String nickname, LocalDate birthday, Gender gender,
                 String profileImageUrl, Boolean notificationsEnabled, TeacherPersona teacherPersona) {
        this.email = email;
        this.nickname = nickname;
        this.birthday = birthday;
        this.gender = gender;
        this.profileImageUrl = profileImageUrl;
        this.notificationsEnabled = notificationsEnabled;
        this.teacherPersona = teacherPersona;
    }

    public static User create(String email, String nickname, LocalDate birthday,
                              Gender gender, String profileImageUrl) {
        return User.builder()
                .email(email)
                .nickname(nickname)
                .birthday(birthday)
                .gender(gender)
                .profileImageUrl(profileImageUrl)
                .notificationsEnabled(true)
                .teacherPersona(TeacherPersona.FRIENDLY)
                .build();
    }

    // === 수정 메서드 (마이페이지 프로필/알림 수정) ===
    // 마이페이지 PATCH 에서 전달된 필드만 변경 감지(dirty checking)로 반영하기 위한 메서드.

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    public void changeProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public void changeNotificationEnabled(Boolean notificationsEnabled) {
        this.notificationsEnabled = notificationsEnabled;
    }

    public void changeTeacherPersona(TeacherPersona teacherPersona) {
        this.teacherPersona = teacherPersona;
    }
}
