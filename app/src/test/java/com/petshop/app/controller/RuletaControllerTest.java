package com.petshop.app.controller;

import com.petshop.app.model.CartItem;
import com.petshop.app.model.Category;
import com.petshop.app.service.AdminGuard;
import com.petshop.app.service.JwtUtil;
import com.petshop.app.service.RouletteService;
import com.petshop.app.service.VentaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuletaControllerTest {

    private RouletteService roulette;
    private VentaService ventas;
    private RuletaController controller;
    private String customerToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        roulette = mock(RouletteService.class);
        ventas = mock(VentaService.class);
        JwtUtil jwtUtil = new JwtUtil("test-secret-test-secret-test-secret-test-secret", 60_000);
        controller = new RuletaController(roulette, ventas, jwtUtil, new AdminGuard(jwtUtil));
        customerToken = jwtUtil.generateToken("user-1", "u@example.test", "CUSTOMER");
        adminToken = jwtUtil.generateToken("admin-1", "a@example.test", "ADMIN");
        Category toys = new Category("juguetes", "Juguetes", "#FDE2E4");
        when(roulette.wheel()).thenReturn(List.of(new Category("gatos", "Gatos", "#BFEAF5"), toys));
        when(roulette.validUntil()).thenReturn(Instant.parse("2026-10-20T03:00:00Z"));
        when(roulette.todaysCategory("user-1")).thenReturn(Optional.empty());
        when(roulette.spin("user-1")).thenReturn(new RouletteService.Spin(toys, LocalDate.parse("2026-10-19"), false));
        when(roulette.spin("guest:abc")).thenReturn(new RouletteService.Spin(toys, LocalDate.parse("2026-10-19"), true));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> body(ResponseEntity<?> response) {
        return (Map<String, Object>) response.getBody();
    }

    @Test
    void theStateListsTheWheelAndSaysThatThePersonHasNotSpunYet() {
        Map<String, Object> state = body(controller.state(customerToken, null));

        assertThat(state).containsEntry("spun", false).containsEntry("percent", 10).containsEntry("categoryId", null);
        assertThat((List<?>) state.get("categories")).hasSize(2);
    }

    @Test
    void spinningReturnsTheWonCategoryAndWhenItExpires() {
        Map<String, Object> result = body(controller.spin(customerToken, null));

        assertThat(result).containsEntry("categoryId", "juguetes").containsEntry("categoryName", "Juguetes")
                .containsEntry("alreadySpun", false).containsEntry("validUntil", "2026-10-20T03:00:00Z");
    }

    @Test
    void guestsAreIdentifiedByTheirGuestIdAndRepeatedSpinsAreFlagged() {
        Map<String, Object> result = body(controller.spin(null, "abc"));

        assertThat(result).containsEntry("alreadySpun", true).containsEntry("categoryId", "juguetes");
    }

    @Test
    void withoutTokenNorGuestIdItIsUnauthorizedAndAdminsDoNotPlay() {
        assertThat(controller.spin(null, null).getStatusCode().value()).isEqualTo(401);
        assertThat(controller.state("basura", " ").getStatusCode().value()).isEqualTo(401);
        assertThat(controller.spin(adminToken, null).getStatusCode().value()).isEqualTo(403);
        assertThat(controller.state(adminToken, null).getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void theCartDiscountIsComputedOnTheServerFromTheRealCart() {
        List<CartItem> cart = List.of(new CartItem("p2", "Pelota", "x", 2, 500));
        when(ventas.cartOf("user-1")).thenReturn(cart);
        when(roulette.discountFor("user-1", cart)).thenReturn(new RouletteService.Applied("juguetes", "Juguetes", 100.0));

        Map<String, Object> discount = body(controller.cartDiscount(customerToken, null));

        assertThat(discount).containsEntry("amount", 100.0).containsEntry("categoryName", "Juguetes").containsEntry("percent", 10);
    }

    @Test
    void theCartDiscountIsZeroWhenThereIsNothingToDiscount() {
        when(ventas.cartOf(org.mockito.ArgumentMatchers.anyString())).thenReturn(List.of());

        Map<String, Object> discount = body(controller.cartDiscount(customerToken, null));

        assertThat(discount).containsEntry("amount", 0.0).containsEntry("categoryId", null);
    }
}
