package com.teaching.backend.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI teachingOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("TeachING Backend API")
                        .description("TeachING 백엔드 API 문서")
                        .version("v1.0.0"));
    }
}