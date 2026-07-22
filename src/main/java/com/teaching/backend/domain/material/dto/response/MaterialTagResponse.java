package com.teaching.backend.domain.material.dto.response;

import com.teaching.backend.domain.tag.entity.MaterialTag;

public record MaterialTagResponse(
        Long tagId,
        String tagName
) {

    public static MaterialTagResponse from(MaterialTag materialTag) {
        return new MaterialTagResponse(
                materialTag.getTag().getId(),
                materialTag.getTag().getName()
        );
    }
}
