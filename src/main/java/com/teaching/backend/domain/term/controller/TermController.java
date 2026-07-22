package com.teaching.backend.domain.term.controller;

import com.teaching.backend.domain.term.dto.TermResponse;
import com.teaching.backend.domain.term.exception.TermSuccessCode;
import com.teaching.backend.domain.term.service.TermService;
import com.teaching.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@Tag(name = "Term", description = "약관 관련 API")
@RestController
@RequestMapping("/api/v1/terms")
@RequiredArgsConstructor
public class TermController {

private final TermService termService;

    @GetMapping
    @Operation(
            summary = "약관 목록 조회",
            description = "회원가입 화면에서 약관 목록을 조회하는 API"
    )
    public ApiResponse<List<TermResponse>> getTerms() {
        List<TermResponse> result = termService.getTerms();
        return ApiResponse.onSuccess(TermSuccessCode.TERM_LIST_FETCH_SUCCESS, result);
    }


}
