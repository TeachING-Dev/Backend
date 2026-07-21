package com.teaching.backend.domain.user.dto;

import com.teaching.backend.domain.user.entity.User;

/**
 * [PATCH] /users/me 응답 result.
 */
public record UserUpdateResponseDto(
        Long userId,
        String nickname,
        String profileImageUrl,
        Boolean notificationEnabled
) {

    public static UserUpdateResponseDto from(User user) {
        return new UserUpdateResponseDto(
                user.getId(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getNotificationsEnabled()
        );
    }
}
