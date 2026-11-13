package com.petshop.app.controller;

import com.petshop.app.service.AdminGuard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.petshop.app.config.AdminOnly;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;

    private final AdminGuard adminGuard;
    private final Path uploadsDir;

    public UploadController(AdminGuard adminGuard, @Value("${petshop.uploads.dir:uploads}") String uploadsDirPath) {
        this.adminGuard = adminGuard;
        this.uploadsDir = Path.of(uploadsDirPath).toAbsolutePath().normalize();
    }

    @AdminOnly
    @PostMapping
    public ResponseEntity<?> upload(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                     @RequestParam("file") MultipartFile file) {
        if (!adminGuard.isAdmin(token)) {
            return adminGuard.denied(token);
        }

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Archivo vacío"));
        }

        if (file.getSize() > MAX_SIZE_BYTES) {
            return ResponseEntity.badRequest().body(Map.of("error", "El archivo no puede superar los 5MB"));
        }

        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        String extension = originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase()
                : "";
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Formato no permitido. Usá jpg, png, gif o webp"));
        }

        String filename = UUID.randomUUID() + "." + extension;

        try {
            Files.createDirectories(uploadsDir);
            Path target = uploadsDir.resolve(filename).normalize();
            if (!target.startsWith(uploadsDir)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Nombre de archivo inválido"));
            }
            file.transferTo(target);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "No se pudo guardar el archivo"));
        }

        return ResponseEntity.ok(Map.of("url", "/uploads/" + filename));
    }
}
