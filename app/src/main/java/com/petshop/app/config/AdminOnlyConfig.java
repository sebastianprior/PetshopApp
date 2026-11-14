package com.petshop.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Activa el control previo de las operaciones marcadas con {@link AdminOnly}. */
@Configuration
public class AdminOnlyConfig implements WebMvcConfigurer {

    private final AdminOnlyInterceptor adminOnlyInterceptor;

    public AdminOnlyConfig(AdminOnlyInterceptor adminOnlyInterceptor) {
        this.adminOnlyInterceptor = adminOnlyInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminOnlyInterceptor).addPathPatterns("/api/**");
    }
}
