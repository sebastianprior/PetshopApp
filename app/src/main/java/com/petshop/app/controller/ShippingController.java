package com.petshop.app.controller;

import com.petshop.app.dto.ShippingConfigDTO;
import com.petshop.app.dto.ShippingConfigDTO.ZoneDTO;
import com.petshop.app.dto.ShippingQuoteDTO;
import com.petshop.app.model.ShippingZone;
import com.petshop.app.model.SiteSettings;
import com.petshop.app.repository.ShippingZoneRepository;
import com.petshop.app.repository.SiteSettingsRepository;
import com.petshop.app.service.AdminGuard;
import com.petshop.app.service.ShopRules;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Costo de envío según el código postal. La cotización es pública (el carrito la consulta); las zonas las edita solo un
 * administrador. El envío gratis no se refleja acá porque depende del carrito: lo resuelve el checkout.
 */
@RestController
@RequestMapping("/api/shipping")
public class ShippingController {

    static final int MIN_POSTAL = 1000;
    static final int MAX_POSTAL = 9999;
    static final int MAX_ZONES = 40;
    static final int MAX_NAME_LENGTH = 60;
    static final int MAX_ETA_LENGTH = 60;
    static final double MAX_COST = 1_000_000;

    private final ShippingZoneRepository zoneRepository;
    private final SiteSettingsRepository settingsRepository;
    private final ShopRules shopRules;
    private final AdminGuard adminGuard;

    public ShippingController(ShippingZoneRepository zoneRepository, SiteSettingsRepository settingsRepository,
                              ShopRules shopRules, AdminGuard adminGuard) {
        this.zoneRepository = zoneRepository;
        this.settingsRepository = settingsRepository;
        this.shopRules = shopRules;
        this.adminGuard = adminGuard;
    }

    @GetMapping("/quote")
    public ResponseEntity<?> quote(@RequestParam("postalCode") String postalCode) {
        Integer parsed = ShopRules.parsePostalCode(postalCode);
        if (parsed == null) {
            return error("Ingresá un código postal válido de 4 números.");
        }
        ShopRules.Quote quote = shopRules.quote(parsed);
        return ResponseEntity.ok(new ShippingQuoteDTO(quote.postalCode(), quote.zoneName(), quote.cost(), quote.eta(), quote.matchedZone()));
    }

    @GetMapping("/zones")
    public ShippingConfigDTO config() {
        ShippingConfigDTO dto = new ShippingConfigDTO();
        SiteSettings settings = settingsRepository.findById(SiteSettings.SINGLETON_ID).orElse(null);
        dto.defaultCost = settings == null || settings.shippingDefaultCost == null ? ShopRules.DEFAULT_SHIPPING_COST : settings.shippingDefaultCost;
        dto.defaultEta = settings == null || settings.shippingDefaultEta == null || settings.shippingDefaultEta.isBlank()
                ? ShopRules.DEFAULT_SHIPPING_ETA : settings.shippingDefaultEta;
        for (ShippingZone zone : zoneRepository.findAllByOrderByPostalFromAsc()) {
            dto.zones.add(new ZoneDTO(zone.id, zone.name, zone.postalFrom, zone.postalTo, zone.cost, zone.eta));
        }
        return dto;
    }

    /** Reemplaza todas las zonas y el costo del resto del país en un solo guardado. */
    @PutMapping("/zones")
    @Transactional
    public ResponseEntity<?> replace(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                     @RequestBody ShippingConfigDTO body) {
        if (!adminGuard.isAdmin(token)) {
            return ResponseEntity.status(403).body(Map.of("error", "Requiere permisos de administrador"));
        }
        if (body == null || body.zones == null) {
            return error("Falta la configuración de envíos.");
        }
        if (!validCost(body.defaultCost)) {
            return error("El costo para el resto del país debe estar entre 0 y " + (long) MAX_COST + ".");
        }
        String defaultEta = body.defaultEta == null ? "" : body.defaultEta.trim();
        if (defaultEta.isEmpty() || defaultEta.length() > MAX_ETA_LENGTH) {
            return error("Indicá el plazo para el resto del país (hasta " + MAX_ETA_LENGTH + " caracteres).");
        }
        if (body.zones.size() > MAX_ZONES) {
            return error("Se admiten hasta " + MAX_ZONES + " zonas.");
        }

        List<ShippingZone> zones = new ArrayList<>();
        for (int i = 0; i < body.zones.size(); i++) {
            ZoneDTO dto = body.zones.get(i);
            int number = i + 1;
            String name = dto == null || dto.name == null ? "" : dto.name.trim();
            String eta = dto == null || dto.eta == null ? "" : dto.eta.trim();
            if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
                return error("La zona " + number + " necesita un nombre (hasta " + MAX_NAME_LENGTH + " caracteres).");
            }
            if (dto.postalFrom < MIN_POSTAL || dto.postalTo > MAX_POSTAL || dto.postalFrom > dto.postalTo) {
                return error("La zona \"" + name + "\" tiene un rango de códigos postales inválido (de " + MIN_POSTAL + " a " + MAX_POSTAL
                        + ", y \"desde\" no puede ser mayor que \"hasta\").");
            }
            if (!validCost(dto.cost)) {
                return error("La zona \"" + name + "\" tiene un costo inválido (entre 0 y " + (long) MAX_COST + ").");
            }
            if (eta.isEmpty() || eta.length() > MAX_ETA_LENGTH) {
                return error("La zona \"" + name + "\" necesita un plazo (hasta " + MAX_ETA_LENGTH + " caracteres).");
            }
            zones.add(new ShippingZone(name, dto.postalFrom, dto.postalTo, dto.cost, eta));
        }

        // Ningún código postal puede quedar en dos zonas: sería ambiguo cuánto se cobra
        List<ShippingZone> sorted = zones.stream().sorted(Comparator.comparingInt((ShippingZone z) -> z.postalFrom)).toList();
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).postalFrom <= sorted.get(i - 1).postalTo) {
                return error("Las zonas \"" + sorted.get(i - 1).name + "\" y \"" + sorted.get(i).name + "\" se superponen en el código postal "
                        + sorted.get(i).postalFrom + ".");
            }
        }

        zoneRepository.deleteAllInBatch();
        zoneRepository.saveAll(zones);

        SiteSettings settings = settingsRepository.findById(SiteSettings.SINGLETON_ID).orElseGet(SiteSettings::new);
        settings.shippingDefaultCost = body.defaultCost;
        settings.shippingDefaultEta = defaultEta;
        settings.shippingSeeded = true;
        settingsRepository.save(settings);

        return ResponseEntity.ok(config());
    }

    private static boolean validCost(double cost) {
        return cost >= 0 && cost <= MAX_COST && !Double.isNaN(cost);
    }

    private static ResponseEntity<?> error(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}
