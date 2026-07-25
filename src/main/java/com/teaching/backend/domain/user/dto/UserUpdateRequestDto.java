package com.teaching.backend.domain.user.dto;

import org.springframework.web.multipart.MultipartFile;

/**
 * [PATCH] /users/me 요청 바디 (부분 수정, multipart/form-data).
 * 전달된 필드만 반영한다. 최소 1개 이상 있어야 한다.
 * profileImage 를 같이 보내면 즉시 S3에 업로드하고 그 결과 URL을 프로필에 반영한다.
 * 닉네임은 더 이상 필수가 아니라, 빈 값으로 오면 변경하지 않은 것으로 취급한다.
 * birthYear/birthMonth/birthDay 는 셋 다 오거나 셋 다 없어야 하며, 일부만 오면 에러.
 *
 * 알림 수신 여부는 /users/me/notifications 로 일원화되어 있어 여기서는 다루지 않는다.
 */
public record UserUpdateRequestDto(
        String nickname,
        MultipartFile profileImage,
        Integer birthYear,
        Integer birthMonth,
        Integer birthDay
) {

    /** 수정할 값이 하나도 없으면 true (PROFILE_NO_UPDATE_FIELD) */
    public boolean isEmpty() {
        return !hasNickname() && !hasProfileImage() && !hasAnyBirthField();
    }

    public boolean hasNickname() {
        return nickname != null && !nickname.isBlank();
    }

    public boolean hasProfileImage() {
        return profileImage != null && !profileImage.isEmpty();
    }

    public boolean hasAnyBirthField() {
        return birthYear != null || birthMonth != null || birthDay != null;
    }

    public boolean hasAllBirthFields() {
        return birthYear != null && birthMonth != null && birthDay != null;
    }
}
