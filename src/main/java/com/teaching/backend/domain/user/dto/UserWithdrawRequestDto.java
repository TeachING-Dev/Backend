package com.teaching.backend.domain.user.dto;

/**
 * [DELETE] /users/me 요청 바디.
 * reason 은 WithdrawalReason enum 값(REJOIN/RARELY_USED/LOW_ACCURACY/ETC) 중 하나여야 하며,
 * ETC 인 경우 reasonDetail 이 필수다. isConfirmed 가 true 여야 탈퇴가 진행된다.
 */
public record UserWithdrawRequestDto(
        String reason,
        String reasonDetail,
        Boolean isConfirmed
) {
}
