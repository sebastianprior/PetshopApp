package com.petshop.app.controller;

import com.petshop.app.dto.FaqDTO;
import com.petshop.app.model.FaqItem;
import com.petshop.app.model.SiteSettings;
import com.petshop.app.repository.FaqItemRepository;
import com.petshop.app.repository.SiteSettingsRepository;
import com.petshop.app.service.AdminGuard;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Preguntas frecuentes: lectura pública (la página FAQ y Salchi Bot), edición solo para administradores. */
@RestController
@RequestMapping("/api/faq")
public class FaqController {

    static final int MAX_ITEMS = 30;
    static final int MAX_QUESTION_LENGTH = 200;
    static final int MAX_ANSWER_LENGTH = 1000;

    private final FaqItemRepository repository;
    private final SiteSettingsRepository settingsRepository;
    private final AdminGuard adminGuard;

    public FaqController(FaqItemRepository repository, SiteSettingsRepository settingsRepository, AdminGuard adminGuard) {
        this.repository = repository;
        this.settingsRepository = settingsRepository;
        this.adminGuard = adminGuard;
    }

    @GetMapping
    public List<FaqDTO> list() {
        return repository.findAllByOrderByPositionAscIdAsc().stream().map(FaqController::toDto).toList();
    }

    /** Reemplaza toda la lista, en el orden recibido (así el administrador agrega, edita, reordena y borra en un solo guardado). */
    @PutMapping
    @Transactional
    public ResponseEntity<?> replace(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                     @RequestBody List<FaqDTO> body) {
        if (!adminGuard.isAdmin(token)) {
            return ResponseEntity.status(403).body(Map.of("error", "Requiere permisos de administrador"));
        }
        if (body == null) {
            return error("Falta la lista de preguntas.");
        }
        if (body.size() > MAX_ITEMS) {
            return error("Se admiten hasta " + MAX_ITEMS + " preguntas.");
        }

        List<FaqItem> items = new ArrayList<>();
        for (int i = 0; i < body.size(); i++) {
            FaqDTO dto = body.get(i);
            String question = dto == null || dto.question == null ? "" : dto.question.trim();
            String answer = dto == null || dto.answer == null ? "" : dto.answer.trim();
            if (question.isEmpty() || answer.isEmpty()) {
                return error("La pregunta " + (i + 1) + " necesita pregunta y respuesta.");
            }
            if (question.length() > MAX_QUESTION_LENGTH) {
                return error("La pregunta " + (i + 1) + " admite hasta " + MAX_QUESTION_LENGTH + " caracteres.");
            }
            if (answer.length() > MAX_ANSWER_LENGTH) {
                return error("La respuesta " + (i + 1) + " admite hasta " + MAX_ANSWER_LENGTH + " caracteres.");
            }
            items.add(new FaqItem(question, answer, i));
        }

        repository.deleteAllInBatch();
        List<FaqItem> saved = repository.saveAll(items);

        // Si queda vacía a propósito, que no se vuelvan a cargar los ejemplos al reiniciar
        SiteSettings settings = settingsRepository.findById(SiteSettings.SINGLETON_ID).orElseGet(SiteSettings::new);
        settings.faqSeeded = true;
        settingsRepository.save(settings);

        return ResponseEntity.ok(saved.stream().map(FaqController::toDto).toList());
    }

    private static FaqDTO toDto(FaqItem item) {
        return new FaqDTO(item.id, item.question, item.answer);
    }

    private static ResponseEntity<?> error(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}
