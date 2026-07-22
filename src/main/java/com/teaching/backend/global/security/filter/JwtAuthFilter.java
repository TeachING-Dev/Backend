package com.teaching.backend.global.security.filter;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.teaching.backend.global.apiPayload.code.BaseErrorCode;
import com.teaching.backend.global.apiPayload.code.GlobalErrorCode;
import com.teaching.backend.global.response.ApiResponse;
import com.teaching.backend.global.security.service.CustomUserDetailsService;
import com.teaching.backend.global.security.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService customUserDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            String token = resolveToken(request);

            if (token != null && jwtUtil.isValid(token)) {
                Long userId = jwtUtil.getUserId(token);
                UserDetails userDetails = customUserDetailsService.loadUserByUsername(String.valueOf(userId));
                Authentication auth = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        } catch (Exception e) {
            // 토큰 파싱/검증 단계에서만 나는 예외를 여기서 401로 응답
            ObjectMapper mapper = new ObjectMapper();
            BaseErrorCode code = GlobalErrorCode.UNAUTHORIZED;

            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(code.getStatus().value());

            ApiResponse<Void> errorResponse = ApiResponse.onFailure(code, null);
            mapper.writeValue(response.getOutputStream(), errorResponse);
            return; // 여기서 끝 — doFilter 호출 안 하고 체인 중단
        }

        // 토큰 없음 / 토큰 검증 정상 완료 → 항상 여기로 와서 다음 필터로 넘어감
        filterChain.doFilter(request, response);
    }

    // Authorization 헤더 우선, 없으면 accessToken 쿠키에서 조회
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.replace("Bearer ", "");
        }

        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("accessToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }

        return null;
    }
}