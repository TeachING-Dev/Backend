package com.teaching.backend.domain.user.dto;

import com.teaching.backend.domain.user.entity.Account;
import com.teaching.backend.domain.user.entity.User;

import java.util.List;

/**
 * [GET] /users/me 응답의 result 부분.
 *
 * record 라서 Jackson 이 필드명/순서를 그대로 직렬화한다.
 * 응답 필드명(birthDate, notificationEnabled 등)은 프론트와 합의된 API 계약이라 그대로 두고,
 * 내부에서 값을 꺼낼 때만 main User 의 getter(getBirthday / getNotificationsEnabled)에 맞춘다.
 *  - birthDate  : "2001-03-15"          (LocalDate#toString)
 *  - createdAt  : "2026-06-01T12:00:00" (LocalDateTime#toString, ISO-8601)
 *
 * main User 에는 accounts 컬렉션이 없으므로, 연동 계정 목록은 서비스에서
 * AccountRepository 로 따로 조회해 넘겨준다.
 */
public record UserInfoResponseDto(
        Long userId,
        String email,
        String nickname,
        String birthDate,
        String profileImageUrl,
        Boolean notificationEnabled,
        String teacherPersona,
        List<AccountDto> accounts,
        String createdAt
) {

    /** 연동 소셜 계정 (provider 만 노출) */
    public record AccountDto(String provider) {
    }

    public static UserInfoResponseDto from(User user, List<Account> accounts) {
        List<AccountDto> accountDtos = accounts.stream()
                .map(account -> new AccountDto(account.getProvider().name()))
                .toList();

        return new UserInfoResponseDto(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getBirthday() != null ? user.getBirthday().toString() : null,
                user.getProfileImageUrl(),
                user.getNotificationsEnabled(),
                user.getTeacherPersona() != null ? user.getTeacherPersona().name() : null,
                accountDtos,
                user.getCreatedAt() != null ? user.getCreatedAt().toString() : null
        );
    }
}
