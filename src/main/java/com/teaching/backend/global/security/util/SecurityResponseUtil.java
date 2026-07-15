package com.teaching.backend.global.security.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teaching.backend.global.apiPayload.code.BaseErrorCode;
import com.teaching.backend.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class SecurityResponseUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void sendErrorResponse(HttpServletResponse response, BaseErrorCode errorCode) throws IOException {
        // 응답 Content-Type, HTTP 상태코드 정의
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(errorCode.getStatus().value());

        // Response Body에 응답통일한 객체 넣기
        ApiResponse<Void> errorResponse = ApiResponse.onFailure(errorCode, null);

        // 실제 Response로 덮어쓰기
        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}