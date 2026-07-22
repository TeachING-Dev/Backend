package com.teaching.backend.global.config;


import com.teaching.backend.global.security.filter.JwtAuthFilter;
import com.teaching.backend.global.security.handler.CustomAccessDenied;
import com.teaching.backend.global.security.handler.CustomEntryPoint;
import com.teaching.backend.global.security.handler.OAuthSuccessHandler;
import com.teaching.backend.global.security.service.CustomOAuthService;
import com.teaching.backend.global.security.service.CustomUserDetailsService;
import com.teaching.backend.global.security.util.JwtUtil;
import lombok.RequiredArgsConstructor;
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

    private final String[] allowUris = {
            "/swagger-ui/**",
            "/swagger-resources/**",
            "/v3/api-docs/**",
            "/api/v1/auth/**",
            "/oauth2/**",
            "/login/oauth2/**",
            "/oauth/redirect"
    };


    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
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
        configuration.setAllowedOrigins(List.of(
                "https://frontend-seven-ivory-16.vercel.app"
        ));
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
