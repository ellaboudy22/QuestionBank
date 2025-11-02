package com.questionbank.QuestionBank.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Swagger/OpenAPI configuration for API documentation
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Question Bank API")
                        .version("1.0")
                        .description("QuestionBank Management System - All endpoints are publicly accessible")
                );
    }
}
