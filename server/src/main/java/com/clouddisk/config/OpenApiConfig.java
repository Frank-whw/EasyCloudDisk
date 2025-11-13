package com.clouddisk.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档配置，定义接口文档信息及 JWT 安全方案。
 */
@Configuration
public class OpenApiConfig {

    /**
     * 配置 OpenAPI 核心元数据及安全要求。
     */
    @Bean
    public OpenAPI openAPI() {
        SecurityScheme bearerScheme = new SecurityScheme()
                .name("Authorization")
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");

        return new OpenAPI()
                .info(new Info()
                        .title("Cloud Disk API")
                        .version("1.0.0")
                        .description("云盘系统 API 文档"))
                .components(new Components().addSecuritySchemes("bearer", bearerScheme))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
