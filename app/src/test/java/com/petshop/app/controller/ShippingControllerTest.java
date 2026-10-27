package com.petshop.app.controller;

import com.petshop.app.dto.ShippingConfigDTO;
import com.petshop.app.dto.ShippingConfigDTO.ZoneDTO;
import com.petshop.app.dto.ShippingQuoteDTO;
import com.petshop.app.model.ShippingZone;
import com.petshop.app.model.SiteSettings;
import com.petshop.app.repository.ProductRepository;
import com.petshop.app.repository.ProductVariantRepository;
import com.petshop.app.repository.ShippingZoneRepository;
import com.petshop.app.repository.SiteSettingsRepository;
import com.petshop.app.service.AdminGuard;
import com.petshop.app.service.JwtUtil;
import com.petshop.app.service.ShopRules;
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

class ShippingControllerTest {

    private ShippingZoneRepository zoneRepository;
    private SiteSettingsRepository settingsRepository;
    private ShippingController controller;
    private String adminToken;
    private String customerToken;
    private List<ShippingZone> stored;
    private SiteSettings settings;

    @BeforeEach
    void setUp() {
        zoneRepository = mock(ShippingZoneRepository.class);
        settingsRepository = mock(SiteSettingsRepository.class);
        JwtUtil jwtUtil = new JwtUtil("test-secret-test-secret-test-secret-test-secret", 60_000);
        ShopRules rules = new ShopRules(settingsRepository, mock(ProductRepository.class), mock(ProductVariantRepository.class), zoneRepository);
        controller = new ShippingController(zoneRepository, settingsRepository, rules, new AdminGuard(jwtUtil));
        adminToken = jwtUtil.generateToken("admin-1", "admin@example.com", "ADMIN");
        customerToken = jwtUtil.generateToken("user-1", "cliente@example.com", "CUSTOMER");

        stored = new ArrayList<>(List.of(
                new ShippingZone("CABA", 1000, 1499, 1200.0, "24 a 48 hs"),
                new ShippingZone("GBA", 1500, 1999, 1500.0, "2 a 3 días hábiles")));
        settings = new SiteSettings();
        settings.shippingDefaultCost = 2500.0;
        settings.shippingDefaultEta = "4 a 7 días hábiles";
        when(zoneRepository.findAllByOrderByPostalFromAsc()).thenAnswer(inv -> new ArrayList<>(stored));
        when(zoneRepository.saveAll(any())).thenAnswer(inv -> {
            stored = new ArrayList<>((List<ShippingZone>) inv.getArgument(0));
            return stored;
        });
        when(settingsRepository.findById(SiteSettings.SINGLETON_ID)).thenAnswer(inv -> Optional.ofNullable(settings));
        when(settingsRepository.save(any(SiteSettings.class))).thenAnswer(inv -> {
            settings = inv.getArgument(0);
            return settings;
        });
    }

    private ShippingConfigDTO config(double defaultCost, String defaultEta, ZoneDTO... zones) {
        ShippingConfigDTO dto = new ShippingConfigDTO();
        dto.defaultCost = defaultCost;
        dto.defaultEta = defaultEta;
        dto.zones = new ArrayList<>(List.of(zones));
        return dto;
    }

    private ZoneDTO zone(String name, int from, int to, double cost, String eta) {
        return new ZoneDTO(null, name, from, to, cost, eta);
    }

    private ShippingQuoteDTO quote(String cp) {
        return (ShippingQuoteDTO) controller.quote(cp).getBody();
    }

    @Test
    void quotesTheCostAndTheEtaOfTheZoneThePostalCodeBelongsTo() {
        ShippingQuoteDTO caba = quote("1425");
        assertThat(caba.zoneName).isEqualTo("CABA");
        assertThat(caba.cost).isEqualTo(1200.0);
        assertThat(caba.eta).isEqualTo("24 a 48 hs");
        assertThat(caba.matchedZone).isTrue();

        assertThat(quote("1500").zoneName).isEqualTo("GBA");
        assertThat(quote("1499").zoneName).isEqualTo("CABA");
        assertThat(quote("1999").zoneName).isEqualTo("GBA");
    }

    @Test
    void anyOtherPostalCodeGetsTheRestOfTheCountry() {
        ShippingQuoteDTO other = quote("5000");

        assertThat(other.zoneName).isEqualTo("Resto del país");
        assertThat(other.cost).isEqualTo(2500.0);
        assertThat(other.eta).isEqualTo("4 a 7 días hábiles");
        assertThat(other.matchedZone).isFalse();
    }

    @Test
    void acceptsTheFullArgentinePostalCodeFormat() {
        assertThat(quote("C1425ABC").zoneName).isEqualTo("CABA");
        assertThat(quote(" c1425abc ").postalCode).isEqualTo(1425);
        assertThat(quote("B1900").postalCode).isEqualTo(1900);
    }

    @Test
    void rejectsPostalCodesThatAreNotFourDigits() {
        for (String bad : new String[] {"", "abc", "142", "14255", "1425x", "1 425"}) {
            assertThat(controller.quote(bad).getStatusCode().value()).as(bad).isEqualTo(400);
        }
    }

    @Test
    void anyoneCanReadTheZones() {
        ShippingConfigDTO config = controller.config();

        assertThat(config.zones).extracting(z -> z.name).containsExactly("CABA", "GBA");
        assertThat(config.defaultCost).isEqualTo(2500.0);
    }

    @Test
    void onlyAdminsCanReplaceTheZones() {
        ShippingConfigDTO body = config(2000, "5 días", zone("Z", 1000, 1100, 10, "1 día"));

        assertThat(controller.replace(customerToken, body).getStatusCode().value()).isEqualTo(403);
        assertThat(controller.replace(null, body).getStatusCode().value()).isEqualTo(403);
        verify(zoneRepository, never()).deleteAllInBatch();
    }

    @Test
    void replacesTheZonesAndTheRestOfTheCountryCost() {
        ResponseEntity<?> response = controller.replace(adminToken, config(3000, " 5 a 8 días ",
                zone(" Córdoba ", 5000, 5999, 2000, "3 días"), zone("Mendoza", 5500 + 1000, 5800 + 1000, 2200, "4 días")));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(zoneRepository).deleteAllInBatch();
        assertThat(stored).extracting(z -> z.name).containsExactly("Córdoba", "Mendoza");
        assertThat(settings.shippingDefaultCost).isEqualTo(3000.0);
        assertThat(settings.shippingDefaultEta).isEqualTo("5 a 8 días");
        assertThat(settings.shippingSeeded).isTrue();
        assertThat(quote("5100").cost).isEqualTo(2000.0);
    }

    @Test
    void zonesCanBeEmptyAndEverythingGoesToTheRestOfTheCountry() {
        assertThat(controller.replace(adminToken, config(1800, "3 días")).getStatusCode().value()).isEqualTo(200);
        assertThat(stored).isEmpty();
        assertThat(quote("1425").cost).isEqualTo(1800.0);
    }

    @Test
    void rejectsInvalidZonesWithoutTouchingTheStoredOnes() {
        assertThat(controller.replace(adminToken, config(1, "x", zone("", 1000, 1100, 10, "1 día"))).getStatusCode().value()).isEqualTo(400);
        assertThat(controller.replace(adminToken, config(1, "x", zone("A", 999, 1100, 10, "1 día"))).getStatusCode().value()).isEqualTo(400);
        assertThat(controller.replace(adminToken, config(1, "x", zone("A", 1000, 10000, 10, "1 día"))).getStatusCode().value()).isEqualTo(400);
        assertThat(controller.replace(adminToken, config(1, "x", zone("A", 1200, 1100, 10, "1 día"))).getStatusCode().value()).isEqualTo(400);
        assertThat(controller.replace(adminToken, config(1, "x", zone("A", 1000, 1100, -5, "1 día"))).getStatusCode().value()).isEqualTo(400);
        assertThat(controller.replace(adminToken, config(1, "x", zone("A", 1000, 1100, 10, " "))).getStatusCode().value()).isEqualTo(400);
        assertThat(controller.replace(adminToken, config(-1, "x")).getStatusCode().value()).isEqualTo(400);
        assertThat(controller.replace(adminToken, config(1, " ")).getStatusCode().value()).isEqualTo(400);
        verify(zoneRepository, never()).deleteAllInBatch();
    }

    @Test
    void rejectsZonesThatOverlap() {
        ResponseEntity<?> response = controller.replace(adminToken, config(1, "x",
                zone("A", 1000, 1500, 10, "1 día"), zone("B", 1500, 1600, 10, "1 día")));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().toString()).contains("se superponen");
        verify(zoneRepository, never()).deleteAllInBatch();
    }

    @Test
    void rejectsMoreThanTheMaximumNumberOfZones() {
        List<ZoneDTO> many = new ArrayList<>();
        for (int i = 0; i < 41; i++) {
            many.add(zone("Z" + i, 1000 + i * 10, 1005 + i * 10, 10, "1 día"));
        }
        ShippingConfigDTO body = new ShippingConfigDTO();
        body.defaultCost = 1;
        body.defaultEta = "x";
        body.zones = many;

        assertThat(controller.replace(adminToken, body).getStatusCode().value()).isEqualTo(400);
    }
}
