package com.petshop.app.controller;

import com.petshop.app.dto.SiteSettingsDTO;
import com.petshop.app.model.SiteSettings;
import com.petshop.app.repository.SiteSettingsRepository;
import com.petshop.app.service.AdminGuard;
import com.petshop.app.service.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SiteSettingsControllerTest {

    private SiteSettingsRepository repository;
    private SiteSettingsController controller;
    private String adminToken;
    private String customerToken;

    @BeforeEach
    void setUp() {
        repository = mock(SiteSettingsRepository.class);
        JwtUtil jwtUtil = new JwtUtil("test-secret-test-secret-test-secret-test-secret", 60_000);
        controller = new SiteSettingsController(repository, new AdminGuard(jwtUtil));
        adminToken = jwtUtil.generateToken("admin-1", "admin@example.com", "ADMIN");
        customerToken = jwtUtil.generateToken("user-1", "cliente@example.com", "CUSTOMER");
        when(repository.findById(SiteSettings.SINGLETON_ID)).thenReturn(Optional.empty());
    }

    private SiteSettingsDTO dto(List<String> items, String mode, String text) {
        return new SiteSettingsDTO(items, mode, text);
    }

    @Test
    void withoutSavedSettingsItReturnsTheOriginalDefaults() {
        SiteSettingsDTO result = controller.get();

        assertThat(result.topbarItems).hasSize(3);
        assertThat(result.announcementMode).isEqualTo("AUTO");
    }

    @Test
    void onlyAdminsCanUpdate() {
        assertThat(controller.update(customerToken, dto(List.of("a"), "AUTO", "")).getStatusCode().value()).isEqualTo(403);
        assertThat(controller.update(null, dto(List.of("a"), "AUTO", "")).getStatusCode().value()).isEqualTo(403);
        verify(repository, never()).save(any());
    }

    @Test
    void savesTrimmedItemsDroppingBlanksAndNormalizesTheMode() {
        ResponseEntity<?> response = controller.update(adminToken,
                dto(List.of("  Envío gratis  ", "", "   ", "2x1 los lunes"), "custom", "  Black Friday  "));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        SiteSettingsDTO saved = (SiteSettingsDTO) response.getBody();
        assertThat(saved.topbarItems).containsExactly("Envío gratis", "2x1 los lunes");
        assertThat(saved.announcementMode).isEqualTo("CUSTOM");
        assertThat(saved.announcementText).isEqualTo("Black Friday");
        verify(repository).save(any(SiteSettings.class));
    }

    @Test
    void rejectsInvalidInput() {
        assertThat(controller.update(adminToken, dto(List.of("a"), "XYZ", "")).getStatusCode().value()).isEqualTo(400);
        assertThat(controller.update(adminToken, dto(List.of("a"), "CUSTOM", "  ")).getStatusCode().value()).isEqualTo(400);
        assertThat(controller.update(adminToken, dto(List.of("1", "2", "3", "4", "5", "6"), "AUTO", "")).getStatusCode().value()).isEqualTo(400);
        assertThat(controller.update(adminToken, dto(List.of("x".repeat(81)), "AUTO", "")).getStatusCode().value()).isEqualTo(400);
        assertThat(controller.update(adminToken, dto(List.of("a"), "CUSTOM", "y".repeat(201))).getStatusCode().value()).isEqualTo(400);
        verify(repository, never()).save(any());
    }

    @Test
    void marqueeIsOffByDefaultAndKeepsItsValueWhenTheClientDoesNotSendIt() {
        assertThat(controller.get().announcementMarquee).isFalse();
        assertThat(controller.get().topbarMarquee).isFalse();

        SiteSettingsDTO withMarquee = dto(List.of("a"), "CUSTOM", "Oferta");
        withMarquee.announcementMarquee = true;
        SiteSettingsDTO saved = (SiteSettingsDTO) controller.update(adminToken, withMarquee).getBody();
        assertThat(saved.announcementMarquee).isTrue();
        assertThat(saved.topbarMarquee).isFalse();

        SiteSettings stored = new SiteSettings();
        stored.announcementMarquee = true;
        when(repository.findById(SiteSettings.SINGLETON_ID)).thenReturn(Optional.of(stored));
        SiteSettingsDTO withoutField = dto(List.of("a"), "CUSTOM", "Oferta");
        SiteSettingsDTO kept = (SiteSettingsDTO) controller.update(adminToken, withoutField).getBody();
        assertThat(kept.announcementMarquee).isTrue();

        withoutField.announcementMarquee = false;
        SiteSettingsDTO turnedOff = (SiteSettingsDTO) controller.update(adminToken, withoutField).getBody();
        assertThat(turnedOff.announcementMarquee).isFalse();
    }

    @Test
    void anEmptyTopbarIsAllowedToHideIt() {
        ResponseEntity<?> response = controller.update(adminToken, dto(List.of(), "OFF", ""));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        SiteSettingsDTO saved = (SiteSettingsDTO) response.getBody();
        assertThat(saved.topbarItems).isEmpty();
        assertThat(saved.announcementMode).isEqualTo("OFF");
    }
}
