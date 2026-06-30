package com.ybank.core.config;
import io.swagger.v3.oas.models.*;import io.swagger.v3.oas.models.info.*;import io.swagger.v3.oas.models.security.*;import org.springframework.context.annotation.*;
@Configuration
class OpenApiConfig {
 @Bean OpenAPI api(){return new OpenAPI().info(new Info().title("YBank Core Banking API").version("1.0.0").description("Digital banking backend for portfolio: auth, accounts, cards, transfers, payments, loans and audit-ready contracts."))
 .addSecurityItem(new SecurityRequirement().addList("bearerAuth")).components(new Components().addSecuritySchemes("bearerAuth",new SecurityScheme().name("bearerAuth").type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));}
}
