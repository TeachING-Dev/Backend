package com.teaching.backend.domain.support.controller;

import com.teaching.backend.domain.support.code.SupportSuccessCode;
import com.teaching.backend.domain.support.dto.SupportContactResponseDto;
import com.teaching.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Support", description = "1:1 문의 관련 API")
@RestController
@RequestMapping("/support")
public class SupportController {

    @Value("${support.kakao-channel-url}")
    private String kakaoChannelUrl;

    @Value("${support.email}")
    private String email;

    /** [GET] /support/contacts — 1:1 문의 채널(카카오톡 채널, 이메일) 조회 */
    @Operation(
            summary = "1:1 문의 채널 조회",
            description = "1:1 문의 화면에서 사용할 카카오톡 채널 URL과 문의 이메일을 조회합니다."
    )
    @GetMapping("/contacts")
    public ApiResponse<SupportContactResponseDto> getContacts() {
        return ApiResponse.onSuccess(
                SupportSuccessCode.CONTACTS_FOUND,
                new SupportContactResponseDto(kakaoChannelUrl, email)
        );
    }
}
