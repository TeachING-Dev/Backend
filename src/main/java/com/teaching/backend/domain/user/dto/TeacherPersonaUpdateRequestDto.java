package com.teaching.backend.domain.user.dto;

/**
 * [PATCH] /users/me/teacher-persona 요청 바디.
 * persona 는 TeacherPersona enum 이름(FRIENDLY/STRICT/CHEERING) 문자열이어야 한다.
 * 없거나 enum 에 없는 값이면 TEACHER_PERSONA_INVALID.
 */
public record TeacherPersonaUpdateRequestDto(
        String persona
) {
}
