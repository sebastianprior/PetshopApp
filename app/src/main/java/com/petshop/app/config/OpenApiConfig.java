package com.petshop.app.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI petshopOpenApi() {
        return new OpenAPI()
                .servers(List.of(new Server().url("http://localhost:8080").description("Desarrollo local")))
                .info(new Info()
                        .title("Petshop API REST v1")
                        .version("1.0.0")
                        .description("Checkout de ventas con Idempotency-Key, errores Problem Details (RFC 9457) "
                                + "y pago autorizado por una pasarela con timeout. Los invitados se identifican "
                                + "con el header X-Guest-Id y los usuarios con X-Auth-Token (JWT)."))
                .components(new Components()
                        .addSecuritySchemes("X-Auth-Token", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Auth-Token")));
    }

    @Bean
    public GroupedOpenApi apiV1() {
        return GroupedOpenApi.builder().group("v1").pathsToMatch("/api/v1/**").build();
    }
}
