package com.microcourse.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * OpenAPI 3.0 自动生成配置
 *
 * <p>访问地址:
 * <ul>
 *   <li>JSON: GET /v3/api-docs</li>
 *   <li>Swagger UI: GET /swagger-ui.html</li>
 * </ul>
 *
 * <p>本配置启用 JWT Bearer 认证, 课程管理域 85 端点通过 Controller @Operation/@Parameter 注解自动生成。</p>
 */
@Profile({"dev", "test", "local"})
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("微课管理平台 API")
                        .description("课程管理域 + 用户认证域 + 微专业域 + 教务域")
                        .version("1.7.0")
                        .contact(new Contact()
                                .name("微课平台开发团队")
                                .email("dev@microcourse.example"))
                        .license(new License()
                                .name("内部使用")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer"))
                .components(new Components()
                        .addSecuritySchemes("Bearer",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT Bearer Token, 格式: Bearer {accessToken}")));
    }
}