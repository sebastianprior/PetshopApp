package com.petshop.app.service;

import com.petshop.app.model.SiteSettings;
import com.petshop.app.repository.FaqItemRepository;
import com.petshop.app.repository.SiteSettingsRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Carga las preguntas frecuentes de ejemplo la primera vez. Queda registrado en la configuración del sitio para que,
 * si el administrador las borra todas a propósito, no vuelvan a aparecer al reiniciar.
 */
@Component
public class FaqSeeder implements ApplicationRunner {

    private final FaqItemRepository faqRepository;
    private final SiteSettingsRepository settingsRepository;

    public FaqSeeder(FaqItemRepository faqRepository, SiteSettingsRepository settingsRepository) {
        this.faqRepository = faqRepository;
        this.settingsRepository = settingsRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        SiteSettings settings = settingsRepository.findById(SiteSettings.SINGLETON_ID).orElseGet(SiteSettings::new);
        if (Boolean.TRUE.equals(settings.faqSeeded)) {
            return;
        }
        if (faqRepository.count() == 0) {
            faqRepository.saveAll(FaqDefaults.items());
        }
        settings.faqSeeded = true;
        settingsRepository.save(settings);
    }
}
