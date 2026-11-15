package com.petshop.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class UploadConfig implements WebMvcConfigurer {

    private final String uploadsDirPath;

    public UploadConfig(@Value("${petshop.uploads.dir:uploads}") String uploadsDirPath) {
        this.uploadsDirPath = uploadsDirPath;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path absolute = Path.of(uploadsDirPath).toAbsolutePath().normalize();
        String location = absolute.toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location);
    }
}
