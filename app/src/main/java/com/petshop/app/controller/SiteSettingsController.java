package com.petshop.app.controller;

import com.petshop.app.dto.SiteSettingsDTO;
import com.petshop.app.model.SiteSettings;
import com.petshop.app.repository.SiteSettingsRepository;
import com.petshop.app.service.AdminGuard;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Textos de las barras de anuncio de la tienda: lectura pública, edición solo para administradores. */
@RestController
@RequestMapping("/api/settings")
public class SiteSettingsController {

    static final List<String> DEFAULT_TOPBAR = List.of(
            "🚚 Envíos a todo el país", "💳 6 cuotas sin interés", "🏷️ Ofertas semanales");
    static final Set<String> MODES = Set.of("AUTO", "CUSTOM", "OFF");
    static final int MAX_TOPBAR_ITEMS = 5;
    static final int MAX_TOPBAR_LENGTH = 80;
    static final int MAX_ANNOUNCEMENT_LENGTH = 200;

    private final SiteSettingsRepository repository;
    private final AdminGuard adminGuard;

    public SiteSettingsController(SiteSettingsRepository repository, AdminGuard adminGuard) {
        this.repository = repository;
        this.adminGuard = adminGuard;
    }

    @GetMapping
    public SiteSettingsDTO get() {
        return repository.findById(SiteSettings.SINGLETON_ID).map(SiteSettingsController::toDto).orElseGet(SiteSettingsController::defaults);
    }

    @PutMapping
    public ResponseEntity<?> update(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                    @RequestBody SiteSettingsDTO body) {
        if (!adminGuard.isAdmin(token)) {
            return ResponseEntity.status(403).body(Map.of("error", "Requiere permisos de administrador"));
        }

        List<String> items = new ArrayList<>();
        if (body.topbarItems != null) {
            for (String item : body.topbarItems) {
                if (item != null && !item.isBlank()) {
                    items.add(item.trim());
                }
            }
        }
        if (items.size() > MAX_TOPBAR_ITEMS) {
            return error("La barra superior admite hasta " + MAX_TOPBAR_ITEMS + " textos.");
        }
        if (items.stream().anyMatch(i -> i.length() > MAX_TOPBAR_LENGTH)) {
            return error("Cada texto de la barra superior admite hasta " + MAX_TOPBAR_LENGTH + " caracteres.");
        }

        String mode = body.announcementMode == null ? "AUTO" : body.announcementMode.trim().toUpperCase();
        if (!MODES.contains(mode)) {
            return error("Modo de anuncio inválido. Usá AUTO, CUSTOM u OFF.");
        }
        String text = body.announcementText == null ? "" : body.announcementText.trim();
        if (text.length() > MAX_ANNOUNCEMENT_LENGTH) {
            return error("El anuncio admite hasta " + MAX_ANNOUNCEMENT_LENGTH + " caracteres.");
        }
        if ("CUSTOM".equals(mode) && text.isEmpty()) {
            return error("Escribí el texto del anuncio o elegí otro modo.");
        }

        SiteSettings settings = repository.findById(SiteSettings.SINGLETON_ID).orElseGet(SiteSettings::new);
        settings.topbarItems = String.join("\n", items);
        settings.announcementMode = mode;
        settings.announcementText = text;
        repository.save(settings);
        return ResponseEntity.ok(toDto(settings));
    }

    private static ResponseEntity<?> error(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    private static SiteSettingsDTO defaults() {
        return new SiteSettingsDTO(DEFAULT_TOPBAR, "AUTO", "");
    }

    private static SiteSettingsDTO toDto(SiteSettings settings) {
        List<String> items = settings.topbarItems == null || settings.topbarItems.isBlank()
                ? List.of() : List.of(settings.topbarItems.split("\n"));
        return new SiteSettingsDTO(items, settings.announcementMode == null ? "AUTO" : settings.announcementMode,
                settings.announcementText == null ? "" : settings.announcementText);
    }
}
