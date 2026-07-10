package com.teaching.backend.domain;

import com.teaching.backend.domain.common.BaseSoftDeleteEntity;
import com.teaching.backend.domain.common.BaseTimeEntity;
import com.teaching.backend.domain.enums.Gender;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Getter
@NoArgsConstructor(access= AccessLevel.PROTECTED)
//탈퇴한 회원은 조회안되게 추가
@SQLRestriction("deleted_at IS NULL")
@Table(name="users")
public class User extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    private Long id;

    @Email
    @Column(nullable = false, unique = true)
    private String email;


    @Column(nullable = false)
    private String nickname;

    @Column(nullable = false)
    private LocalDate birthday;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
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
