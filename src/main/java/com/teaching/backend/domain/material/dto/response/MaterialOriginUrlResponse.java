package com.teaching.backend.domain.material.dto.response;

public record MaterialOriginUrlResponse(
        Long materialId,
        String originUrl
) {

    public static MaterialOriginUrlResponse of(Long materialId, String originUrl) {
        return new MaterialOriginUrlResponse(materialId, originUrl);
    }
}
