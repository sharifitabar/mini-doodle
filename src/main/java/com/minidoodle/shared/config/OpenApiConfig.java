package com.minidoodle.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {

    @Bean
    OpenAPI miniDoodleOpenApi() {
        return new OpenAPI().info(new Info()
                .title("mini-doodle Time Slot API")
                .version("1.0.0")
                .description("Manage time slots in a user's personal calendar. "
                        + "The caller is identified by the required X-User-Id header (demo authentication).")
                .license(new License().name("MIT")));
    }
}
