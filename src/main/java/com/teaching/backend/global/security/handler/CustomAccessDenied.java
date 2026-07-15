package com.teaching.backend.global.security.handler;


import com.teaching.backend.global.apiPayload.code.GlobalErrorCode;
import com.teaching.backend.global.security.util.SecurityResponseUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component // Spring Bean으로 등록해야 SecurityConfig에서 주입받아 사용할 수 있다!
public class CustomAccessDenied implements AccessDeniedHandler {
    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        // 💡 마찬가지로 공통 Util 호출! (상태코드에 맞춰 바꾸거나 동일하게 설정)
        SecurityResponseUtil.sendErrorResponse(response, GlobalErrorCode.FORBIDDEN);
    }
}