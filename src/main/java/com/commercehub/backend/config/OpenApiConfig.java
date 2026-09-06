package com.commercehub.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI commerceHubOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("CommerceHub API")
                        .description("API cho sàn thương mại sản phẩm số CommerceHub")
                        .version("1.0.0")
                        .contact(new Contact().name("CommerceHub")))
                .components(new Components().addSecuritySchemes(
                        BEARER_AUTH,
                        new SecurityScheme()
                                .name(BEARER_AUTH)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                ));
    }

    /**
     * Gắn Bearer JWT cho endpoint cần đăng nhập và để trống security ở endpoint public.
     * Danh sách public phải được giữ đồng bộ với SecurityConfig.
     */
    @Bean
    public OpenApiCustomizer operationSecurityCustomizer() {
        return openApi -> openApi.getPaths().forEach((path, pathItem) ->
                pathItem.readOperationsMap().forEach((method, operation) -> {
                    if (isPublicOperation(method.name(), path)) {
                        operation.setSecurity(List.of());
                    } else {
                        operation.setSecurity(List.of(new SecurityRequirement().addList(BEARER_AUTH)));
                    }
                })
        );
    }

    private boolean isPublicOperation(String method, String path) {
        if ("POST".equals(method) && (
                path.equals("/api/v1/auth/register")
                        || path.equals("/api/v1/auth/login")
                        || path.equals("/api/v1/auth/refresh-token")
                        || path.equals("/api/v1/auth/google")
        )) {
            return true;
        }

        if ("GET".equals(method)) {
            if (path.equals("/api/v1/users/levels") || path.equals("/api/v1/users/{username}")) {
                return true;
            }
            if (path.startsWith("/api/v1/categories")) {
                return true;
            }
            if (path.equals("/api/v1/shops") || path.equals("/api/v1/shops/{slug}")) {
                return true;
            }
            if (path.startsWith("/api/v1/products")) {
                return true;
            }
            if (path.startsWith("/api/v1/product-reviews/")) {
                return true;
            }
            return false;
        }

        return "POST".equals(method) && (
                path.equals("/api/v1/products/search")
                        || path.equals("/api/v1/wallet/deposit/sepay-ipn")
        );
    }
}
