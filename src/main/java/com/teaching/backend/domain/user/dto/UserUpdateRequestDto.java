package com.teaching.backend.domain.user.dto;

/**
 * [PATCH] /users/me 요청 바디 (부분 수정).
 * 전달된 필드(null 이 아닌 필드)만 반영한다. 최소 1개 이상 있어야 한다.
 *
 * 알림 수신 여부는 /users/me/notifications 로 일원화되어 있어 여기서는 다루지 않는다.
 */
public record UserUpdateRequestDto(
        String nickname,
        String profileImageUrl
) {

    /** 수정할 값이 하나도 없으면 true (PROFILE_NO_UPDATE_FIELD) */
    public boolean isEmpty() {
        return nickname == null && profileImageUrl == null;
    }
}
