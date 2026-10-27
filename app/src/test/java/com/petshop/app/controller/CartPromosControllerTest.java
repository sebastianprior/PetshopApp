package com.petshop.app.controller;

import com.petshop.app.dto.CartPromosDTO;
import com.petshop.app.model.Product;
import com.petshop.app.model.ProductVariant;
import com.petshop.app.model.SiteSettings;
import com.petshop.app.repository.ProductRepository;
import com.petshop.app.repository.ProductVariantRepository;
import com.petshop.app.repository.SiteSettingsRepository;
import com.petshop.app.service.AdminGuard;
import com.petshop.app.service.JwtUtil;
import com.petshop.app.service.ShopRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CartPromosControllerTest {

    private SiteSettingsRepository repository;
    private ProductRepository productRepository;
    private ProductVariantRepository variantRepository;
    private CartPromosController controller;
    private String adminToken;
    private String customerToken;
    private SiteSettings stored;

    @BeforeEach
    void setUp() {
        repository = mock(SiteSettingsRepository.class);
        productRepository = mock(ProductRepository.class);
        variantRepository = mock(ProductVariantRepository.class);
        JwtUtil jwtUtil = new JwtUtil("test-secret-test-secret-test-secret-test-secret", 60_000);
        ShopRules shopRules = new ShopRules(repository, productRepository, variantRepository, mock(com.petshop.app.repository.ShippingZoneRepository.class));
        controller = new CartPromosController(repository, productRepository, shopRules, new AdminGuard(jwtUtil));
        adminToken = jwtUtil.generateToken("admin-1", "admin@example.com", "ADMIN");
        customerToken = jwtUtil.generateToken("user-1", "cliente@example.com", "CUSTOMER");

        stored = null;
        when(repository.findById(SiteSettings.SINGLETON_ID)).thenAnswer(inv -> Optional.ofNullable(stored));
        when(repository.save(any(SiteSettings.class))).thenAnswer(inv -> {
            stored = inv.getArgument(0);
            return stored;
        });
        when(variantRepository.findByProductId(anyString())).thenReturn(List.of());
        when(productRepository.findById("g1")).thenReturn(Optional.of(
                new Product("g1", "Llavero", "Marca", 300.0, null, 4.0, "/g.jpg", null, "accesorios", 5)));
    }

    private CartPromosDTO body(boolean freeShipping, double threshold, boolean gift, String giftId, double giftMin) {
        CartPromosDTO dto = new CartPromosDTO();
        dto.freeShippingEnabled = freeShipping;
        dto.freeShippingThreshold = threshold;
        dto.giftEnabled = gift;
        dto.giftProductId = giftId;
        dto.giftMinAmount = giftMin;
        return dto;
    }

    @Test
    void withoutSavedSettingsEverythingIsOff() {
        CartPromosDTO result = controller.get();

        assertThat(result.freeShippingEnabled).isFalse();
        assertThat(result.giftEnabled).isFalse();
        assertThat(result.giftAvailable).isFalse();
    }

    @Test
    void onlyAdminsCanUpdate() {
        assertThat(controller.update(customerToken, body(true, 20000, false, null, 0)).getStatusCode().value()).isEqualTo(403);
        assertThat(controller.update(null, body(true, 20000, false, null, 0)).getStatusCode().value()).isEqualTo(403);
        verify(repository, never()).save(any());
    }

    @Test
    void savesFreeShippingAndTheGiftAndReturnsTheGiftProductData() {
        ResponseEntity<?> response = controller.update(adminToken, body(true, 20000, true, " g1 ", 50000));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        CartPromosDTO saved = (CartPromosDTO) response.getBody();
        assertThat(saved.freeShippingEnabled).isTrue();
        assertThat(saved.freeShippingThreshold).isEqualTo(20000);
        assertThat(saved.giftEnabled).isTrue();
        assertThat(saved.giftProductId).isEqualTo("g1");
        assertThat(saved.giftMinAmount).isEqualTo(50000);
        assertThat(saved.giftProductName).isEqualTo("Llavero");
        assertThat(saved.giftAvailable).isTrue();

        ArgumentCaptor<SiteSettings> captor = ArgumentCaptor.forClass(SiteSettings.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().giftProductId).isEqualTo("g1");
    }

    @Test
    void disablingKeepsThingsOffAndDoesNotRequireTheAmounts() {
        ResponseEntity<?> response = controller.update(adminToken, body(false, 0, false, null, 0));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        CartPromosDTO saved = (CartPromosDTO) response.getBody();
        assertThat(saved.freeShippingEnabled).isFalse();
        assertThat(saved.giftEnabled).isFalse();
    }

    @Test
    void rejectsInvalidAmountsAndGifts() {
        assertThat(controller.update(adminToken, body(true, 0, false, null, 0)).getStatusCode().value()).isEqualTo(400);
        assertThat(controller.update(adminToken, body(true, -5, false, null, 0)).getStatusCode().value()).isEqualTo(400);
        assertThat(controller.update(adminToken, body(true, 20_000_001, false, null, 0)).getStatusCode().value()).isEqualTo(400);
        assertThat(controller.update(adminToken, body(false, 0, true, "g1", 0)).getStatusCode().value()).isEqualTo(400);
        assertThat(controller.update(adminToken, body(false, 0, true, "", 1000)).getStatusCode().value()).isEqualTo(400);
        assertThat(controller.update(adminToken, body(false, 0, true, "no-existe", 1000)).getStatusCode().value()).isEqualTo(400);
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsAGiftWithoutStockOrWithVariants() {
        when(productRepository.findById("g0")).thenReturn(Optional.of(
                new Product("g0", "Agotado", "Marca", 100.0, null, 4.0, "/g.jpg", null, "accesorios", 0)));
        when(productRepository.findById("gv")).thenReturn(Optional.of(
                new Product("gv", "Con talles", "Marca", 100.0, null, 4.0, "/g.jpg", null, "accesorios", 9)));
        when(variantRepository.findByProductId("gv")).thenReturn(List.of(new ProductVariant()));

        assertThat(controller.update(adminToken, body(false, 0, true, "g0", 1000)).getStatusCode().value()).isEqualTo(400);
        assertThat(controller.update(adminToken, body(false, 0, true, "gv", 1000)).getStatusCode().value()).isEqualTo(400);
        verify(repository, never()).save(any());
    }
}
