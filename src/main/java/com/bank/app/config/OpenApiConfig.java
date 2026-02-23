package com.bank.app.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI finCoreOpenApi() {
        final String bearerScheme = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("FinCore Banking API")
                        .description("Secure banking APIs for authentication, transfers, governance, and fraud operations")
                        .version("v1")
                        .contact(new Contact()
                                .name("FinCore Engineering")
                                .email("engineering@fincore.com")))
                .addSecurityItem(new SecurityRequirement().addList(bearerScheme))
                .schemaRequirement(bearerScheme, new SecurityScheme()
                        .name(bearerScheme)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"));
    }
}
