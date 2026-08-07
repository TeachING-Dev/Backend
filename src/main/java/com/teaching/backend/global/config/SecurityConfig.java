package com.teaching.backend.global.config;


import com.teaching.backend.global.security.filter.JwtAuthFilter;
import com.teaching.backend.global.security.handler.CustomAccessDenied;
import com.teaching.backend.global.security.handler.CustomEntryPoint;
import com.teaching.backend.global.security.handler.OAuthSuccessHandler;
import com.teaching.backend.global.security.service.CustomOAuthService;
import com.teaching.backend.global.security.service.CustomUserDetailsService;
import com.teaching.backend.global.security.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@EnableWebSecurity
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;
    private final CustomOAuthService customOAuthService;
    private final OAuthSuccessHandler oAuthSuccessHandler;

    @Value("${cors.allowed-origins}")
    private List<String> corsAllowedOrigins;

    private final String[] allowUris = {
            "/swagger-ui/**",
            "/swagger-resources/**",
            "/v3/api-docs/**",
            "/api/v1/auth/reissue",
            "/api/v1/auth/logout",
            "/api/v1/auth/check-nickname",
            "/oauth2/**",
            "/login/oauth2/**",
            "/oauth/redirect",
            // 카카오페이가 브라우저를 리다이렉트시켜 호출하는 콜백이라 JWT를 실어 보내지 않는다.
            // orderId(추측 불가한 값)+카카오페이가 발급한 pg_token으로만 검증한다.
            "/api/v1/payments/success",
            "/api/v1/payments/cancel",
            "/api/v1/payments/fail"
    };


    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                //cors 설정 추가
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(allowUris).permitAll()

                        .anyRequest().authenticated()
                )

                // 2. 폼 로그인 비활성화
                .formLogin(AbstractHttpConfigurer::disable)
                // 3. 세션 비활성화
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                //4. JWT 필터 추가
                .addFilterBefore(jwtAuthFilter(), UsernamePasswordAuthenticationFilter.class)
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuthService))
                        .successHandler(oAuthSuccessHandler)
                )
                //6. 예외상황 핸들러
                .exceptionHandling(exception->exception
                        .accessDeniedHandler(customAccessDenied())
                        .authenticationEntryPoint(customEntryPoint())
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsAllowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public CustomAccessDenied customAccessDenied() {
        return new CustomAccessDenied();
    }

    @Bean
    public CustomEntryPoint customEntryPoint() {
        return new CustomEntryPoint();
    }

    @Bean
    public JwtAuthFilter jwtAuthFilter()
    {
        return new JwtAuthFilter(jwtUtil, userDetailsService);
    }
}
