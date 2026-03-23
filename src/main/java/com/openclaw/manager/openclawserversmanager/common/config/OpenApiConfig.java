package com.openclaw.manager.openclawserversmanager.common.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        in = SecuritySchemeIn.HEADER,
        description = "Enter your JWT access token"
)
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("OpenClaw Control Plane API")
                        .version("1.0.0")
                        .description("Backend API for managing OpenClaw-based agent infrastructures. "
                                + "Coordinates server inventory, secure secrets, SSH access, deployments, "
                                + "domain provisioning, and SSL automation.")
                        .contact(new Contact()
                                .name("OpenClaw")
                                .url("https://github.com/Reputeo/openclaw-servers-manager"))
                        .license(new License()
                                .name("MIT")));
    }
}
