package com.teaching.backend.user.entity;

import com.teaching.backend.global.common.BaseSoftDeleteEntity;
import com.teaching.backend.user.enums.Gender;
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

    @Column(nullable = false)
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
}
