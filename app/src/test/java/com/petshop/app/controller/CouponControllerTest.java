package com.petshop.app.controller;

import com.petshop.app.model.Coupon;
import com.petshop.app.repository.CouponRepository;
import com.petshop.app.service.AdminGuard;
import com.petshop.app.service.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CouponControllerTest {

    private static final String JWT_SECRET = "test-secret-test-secret-test-secret-test-secret";

    private CouponRepository couponRepository;
    private CouponController controller;
    private String adminToken;
    private String customerToken;

    @BeforeEach
    void setUp() {
        couponRepository = mock(CouponRepository.class);
        JwtUtil jwtUtil = new JwtUtil(JWT_SECRET, 60_000);
        AdminGuard adminGuard = new AdminGuard(jwtUtil);
        controller = new CouponController(couponRepository, adminGuard);
        adminToken = jwtUtil.generateToken("admin-1", "admin@example.com", "ADMIN");
        customerToken = jwtUtil.generateToken("user-1", "cliente@example.com", "CUSTOMER");
    }

    @Test
    void adminCanCreateAValidCoupon() {
        Coupon coupon = new Coupon("verano10", Coupon.DiscountType.PERCENTAGE, 10, 0, null, null);
        when(couponRepository.findByCodeIgnoreCase("VERANO10")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.create(adminToken, coupon);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(coupon.code).isEqualTo("VERANO10");
    }

    @Test
    void rejectsAPercentageDiscountAbove100() {
        Coupon coupon = new Coupon("MEGA200", Coupon.DiscountType.PERCENTAGE, 200, 0, null, null);

        ResponseEntity<?> response = controller.create(adminToken, coupon);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void nonAdminCannotCreateCoupons() {
        Coupon coupon = new Coupon("NOPE10", Coupon.DiscountType.PERCENTAGE, 10, 0, null, null);

        ResponseEntity<?> response = controller.create(customerToken, coupon);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void validateReturnsDiscountForAValidCoupon() {
        Coupon coupon = new Coupon("DESC20", Coupon.DiscountType.PERCENTAGE, 20, 100, null, null);
        coupon.id = 1L;
        when(couponRepository.findByCodeIgnoreCase("DESC20")).thenReturn(Optional.of(coupon));

        ResponseEntity<?> response = controller.validateCode(Map.of("code", "DESC20", "subtotal", 500));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("discountAmount")).isEqualTo(100.0);
    }

    @Test
    void validateRejectsWhenBelowMinimumPurchase() {
        Coupon coupon = new Coupon("MIN1000", Coupon.DiscountType.FIXED, 50, 1000, null, null);
        coupon.id = 1L;
        when(couponRepository.findByCodeIgnoreCase("MIN1000")).thenReturn(Optional.of(coupon));

        ResponseEntity<?> response = controller.validateCode(Map.of("code", "MIN1000", "subtotal", 500));

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void validateRejectsWhenUsesExhausted() {
        Coupon coupon = new Coupon("LIMITADO", Coupon.DiscountType.FIXED, 50, 0, 1, null);
        coupon.id = 1L;
        coupon.usesCount = 1;
        when(couponRepository.findByCodeIgnoreCase("LIMITADO")).thenReturn(Optional.of(coupon));

        ResponseEntity<?> response = controller.validateCode(Map.of("code", "LIMITADO", "subtotal", 500));

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void validateRejectsUnknownCode() {
        when(couponRepository.findByCodeIgnoreCase("FANTASMA")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.validateCode(Map.of("code", "FANTASMA", "subtotal", 500));

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }
}
