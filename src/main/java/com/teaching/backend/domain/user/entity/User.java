package com.teaching.backend.domain.user.entity;

import com.teaching.backend.global.common.BaseSoftDeleteEntity;
import com.teaching.backend.domain.user.enums.Gender;
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

    @Column(nullable = false, unique = true)
    private String nickname;


    private LocalDate birthday;

    @Enumerated(EnumType.STRING)

    private Gender gender;

    private String profileImageUrl;

    @Column(nullable = false)
    private Boolean notificationsEnabled;

    @Builder(access = AccessLevel.PRIVATE)
    private User(String email, String nickname, LocalDate birthday, Gender gender,
                 String profileImageUrl, Boolean notificationsEnabled) {
        this.email = email;
        this.nickname = nickname;
        this.birthday = birthday;
        this.gender = gender;
        this.profileImageUrl = profileImageUrl;
        this.notificationsEnabled = notificationsEnabled;
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
}
