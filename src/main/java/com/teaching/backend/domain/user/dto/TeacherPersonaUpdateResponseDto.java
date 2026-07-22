package com.teaching.backend.domain.user.dto;

import com.teaching.backend.domain.user.enums.TeacherPersona;

/**
 * [PATCH] /users/me/teacher-persona 응답 result.
 */
public record TeacherPersonaUpdateResponseDto(
        String teacherPersona
) {

    public static TeacherPersonaUpdateResponseDto of(TeacherPersona teacherPersona) {
        return new TeacherPersonaUpdateResponseDto(teacherPersona.name());
    }
}
