package com.petshop.app.service;

import com.petshop.app.model.ShippingZone;
import com.petshop.app.model.SiteSettings;
import com.petshop.app.repository.ShippingZoneRepository;
import com.petshop.app.repository.SiteSettingsRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Carga zonas de envío de ejemplo la primera vez (CABA, Gran Buenos Aires y el resto del país). Son solo el punto de
 * partida: el administrador las edita desde el panel. Queda registrado para no volver a cargarlas si las borra a propósito.
 */
@Component
public class ShippingZoneSeeder implements ApplicationRunner {

    private final ShippingZoneRepository zoneRepository;
    private final SiteSettingsRepository settingsRepository;

    public ShippingZoneSeeder(ShippingZoneRepository zoneRepository, SiteSettingsRepository settingsRepository) {
        this.zoneRepository = zoneRepository;
        this.settingsRepository = settingsRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        SiteSettings settings = settingsRepository.findById(SiteSettings.SINGLETON_ID).orElseGet(SiteSettings::new);
        if (Boolean.TRUE.equals(settings.shippingSeeded)) {
            return;
        }
        if (zoneRepository.count() == 0) {
            zoneRepository.saveAll(List.of(
                    new ShippingZone("CABA", 1000, 1499, 1200.0, "24 a 48 hs"),
                    new ShippingZone("Gran Buenos Aires", 1500, 1999, 1500.0, "2 a 3 días hábiles")));
        }
        if (settings.shippingDefaultCost == null) {
            settings.shippingDefaultCost = 2500.0;
        }
        if (settings.shippingDefaultEta == null) {
            settings.shippingDefaultEta = "4 a 7 días hábiles";
        }
        settings.shippingSeeded = true;
        settingsRepository.save(settings);
    }
}
