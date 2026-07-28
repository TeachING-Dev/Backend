package com.teaching.backend.domain.material.service.extract;

import com.teaching.backend.domain.material.dto.extract.ExtractedMaterialContent;
import com.teaching.backend.domain.material.enums.PlatformType;

public interface MaterialContentExtractor {

    boolean supports(PlatformType platformType);

    ExtractedMaterialContent extract(String originalUrl);
}
