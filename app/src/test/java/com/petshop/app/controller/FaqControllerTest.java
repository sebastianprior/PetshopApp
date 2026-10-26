package com.petshop.app.controller;

import com.petshop.app.dto.FaqDTO;
import com.petshop.app.model.FaqItem;
import com.petshop.app.model.SiteSettings;
import com.petshop.app.repository.FaqItemRepository;
import com.petshop.app.repository.SiteSettingsRepository;
import com.petshop.app.service.AdminGuard;
import com.petshop.app.service.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FaqControllerTest {

    private FaqItemRepository repository;
    private SiteSettingsRepository settingsRepository;
    private FaqController controller;
    private String adminToken;
    private String customerToken;
    private List<FaqItem> stored;
    private SiteSettings settings;

    @BeforeEach
    void setUp() {
        repository = mock(FaqItemRepository.class);
        settingsRepository = mock(SiteSettingsRepository.class);
        JwtUtil jwtUtil = new JwtUtil("test-secret-test-secret-test-secret-test-secret", 60_000);
        controller = new FaqController(repository, settingsRepository, new AdminGuard(jwtUtil));
        adminToken = jwtUtil.generateToken("admin-1", "admin@example.com", "ADMIN");
        customerToken = jwtUtil.generateToken("user-1", "cliente@example.com", "CUSTOMER");

        stored = new ArrayList<>(List.of(new FaqItem("¿Q1?", "A1", 0), new FaqItem("¿Q2?", "A2", 1)));
        settings = null;
        when(repository.findAllByOrderByPositionAscIdAsc()).thenAnswer(inv -> new ArrayList<>(stored));
        when(repository.saveAll(any())).thenAnswer(inv -> {
            stored = new ArrayList<>(inv.<Iterable<FaqItem>>getArgument(0) instanceof List<?> l ? (List<FaqItem>) l : List.of());
            return stored;
        });
        when(settingsRepository.findById(SiteSettings.SINGLETON_ID)).thenAnswer(inv -> Optional.ofNullable(settings));
        when(settingsRepository.save(any(SiteSettings.class))).thenAnswer(inv -> {
            settings = inv.getArgument(0);
            return settings;
        });
    }

    private FaqDTO dto(String question, String answer) {
        return new FaqDTO(null, question, answer);
    }

    @Test
    void anyoneCanReadTheListInOrder() {
        List<FaqDTO> list = controller.list();

        assertThat(list).extracting(f -> f.question).containsExactly("¿Q1?", "¿Q2?");
    }

    @Test
    void onlyAdminsCanReplaceTheList() {
        assertThat(controller.replace(customerToken, List.of(dto("¿Q?", "A"))).getStatusCode().value()).isEqualTo(403);
        assertThat(controller.replace(null, List.of(dto("¿Q?", "A"))).getStatusCode().value()).isEqualTo(403);
        verify(repository, never()).deleteAllInBatch();
    }

    @Test
    void replacesTheWholeListKeepingTheReceivedOrderAndTrimmingTexts() {
        ResponseEntity<?> response = controller.replace(adminToken, List.of(dto("  ¿Nueva 1?  ", " Respuesta 1 "), dto("¿Nueva 2?", "Respuesta 2")));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(repository).deleteAllInBatch();
        assertThat(stored).extracting(i -> i.question).containsExactly("¿Nueva 1?", "¿Nueva 2?");
        assertThat(stored).extracting(i -> i.answer).containsExactly("Respuesta 1", "Respuesta 2");
        assertThat(stored).extracting(i -> i.position).containsExactly(0, 1);
    }

    @Test
    void anEmptyListIsAllowedAndMarksTheExamplesAsAlreadyLoaded() {
        ResponseEntity<?> response = controller.replace(adminToken, List.of());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(stored).isEmpty();
        assertThat(settings.faqSeeded).isTrue();
    }

    @Test
    void rejectsInvalidInputWithoutTouchingTheStoredList() {
        assertThat(controller.replace(adminToken, List.of(dto("", "A"))).getStatusCode().value()).isEqualTo(400);
        assertThat(controller.replace(adminToken, List.of(dto("¿Q?", "  "))).getStatusCode().value()).isEqualTo(400);
        assertThat(controller.replace(adminToken, List.of(dto("q".repeat(201), "A"))).getStatusCode().value()).isEqualTo(400);
        assertThat(controller.replace(adminToken, List.of(dto("¿Q?", "a".repeat(1001)))).getStatusCode().value()).isEqualTo(400);
        List<FaqDTO> tooMany = new ArrayList<>();
        for (int i = 0; i < 31; i++) {
            tooMany.add(dto("¿Q" + i + "?", "A"));
        }
        assertThat(controller.replace(adminToken, tooMany).getStatusCode().value()).isEqualTo(400);
        verify(repository, never()).deleteAllInBatch();
    }
}
