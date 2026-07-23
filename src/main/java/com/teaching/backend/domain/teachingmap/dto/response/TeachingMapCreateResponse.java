package com.teaching.backend.domain.teachingmap.dto.response;

import com.teaching.backend.domain.teachingmap.entity.TeachingMap;
import com.teaching.backend.domain.teachingmap.enums.TeachingMapType;
import com.teaching.backend.domain.term.dto.TermResponse;
import com.teaching.backend.domain.term.entity.Term;

import java.time.LocalDateTime;

public record TeachingMapCreateResponse
        (
                Long teachingMapId,
                String title,
                String description,
                Long folderId,
                TeachingMapType type,
                LocalDateTime createdAt
        ){
    public static TeachingMapCreateResponse from(TeachingMap teachingMap) {
        return new TeachingMapCreateResponse(
                teachingMap.getId(),
                teachingMap.getTitle(),
                teachingMap.getDescription(),
                teachingMap.getFolder().getId(),
                teachingMap.getType(),
                teachingMap.getCreatedAt()
        );
    }
}
