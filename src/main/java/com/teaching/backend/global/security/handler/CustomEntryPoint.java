package com.teaching.backend.global.security.handler;


import com.teaching.backend.global.apiPayload.code.GlobalErrorCode;
import com.teaching.backend.global.security.util.SecurityResponseUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        // 💡 공통 Util 호출로 단 한 줄로 축소!
        SecurityResponseUtil.sendErrorResponse(response, GlobalErrorCode.UNAUTHORIZED);
    }
}