package com.teaching.backend.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SignupRequest(
        @NotBlank
        @Size(max = 10, message = "닉네임은 10자 이내여야 합니다.")
        String nickname,

        @NotEmpty
        List<Long> agreedTermIds  // 사용자가 동의한 약관 ID 목록
) {}