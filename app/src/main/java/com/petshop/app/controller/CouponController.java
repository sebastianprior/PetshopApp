package com.petshop.app.controller;

import com.petshop.app.model.Coupon;
import com.petshop.app.repository.CouponRepository;
import com.petshop.app.service.AdminGuard;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.petshop.app.config.AdminOnly;

@RestController
@RequestMapping("/api/coupons")
public class CouponController {

    private final CouponRepository couponRepository;
    private final AdminGuard adminGuard;

    public CouponController(CouponRepository couponRepository, AdminGuard adminGuard) {
        this.couponRepository = couponRepository;
        this.adminGuard = adminGuard;
    }

    @AdminOnly
    @GetMapping
    public ResponseEntity<?> list(@RequestHeader(value = "X-Auth-Token", required = false) String token) {
        if (!adminGuard.isAdmin(token)) {
            return adminGuard.denied(token);
        }
        return ResponseEntity.ok(couponRepository.findAll());
    }

    @AdminOnly
    @PostMapping
    public ResponseEntity<?> create(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                     @RequestBody Coupon coupon) {
        if (!adminGuard.isAdmin(token)) {
            return adminGuard.denied(token);
        }

        ResponseEntity<?> validationError = validate(coupon);
        if (validationError != null) {
            return validationError;
        }

        coupon.code = coupon.code.trim().toUpperCase();
        if (couponRepository.findByCodeIgnoreCase(coupon.code).isPresent()) {
            return ResponseEntity.status(409).body(Map.of("error", "Ya existe un cupón con ese código"));
        }

        coupon.id = null;
        coupon.usesCount = 0;
        couponRepository.save(coupon);
        return ResponseEntity.ok(coupon);
    }

    @AdminOnly
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                     @PathVariable Long id,
                                     @RequestBody Coupon coupon) {
        if (!adminGuard.isAdmin(token)) {
            return adminGuard.denied(token);
        }

        Coupon existing = couponRepository.findById(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cupón no encontrado"));
        }

        ResponseEntity<?> validationError = validate(coupon);
        if (validationError != null) {
            return validationError;
        }

        String newCode = coupon.code.trim().toUpperCase();
        Coupon codeOwner = couponRepository.findByCodeIgnoreCase(newCode).orElse(null);
        if (codeOwner != null && !codeOwner.id.equals(id)) {
            return ResponseEntity.status(409).body(Map.of("error", "Ya existe un cupón con ese código"));
        }

        existing.code = newCode;
        existing.discountType = coupon.discountType;
        existing.discountValue = coupon.discountValue;
        existing.active = coupon.active;
        existing.minPurchase = coupon.minPurchase;
        existing.maxUses = coupon.maxUses;
        existing.expiresAt = coupon.expiresAt;
        couponRepository.save(existing);
        return ResponseEntity.ok(existing);
    }

    @AdminOnly
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                     @PathVariable Long id) {
        if (!adminGuard.isAdmin(token)) {
            return adminGuard.denied(token);
        }
        if (!couponRepository.existsById(id)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cupón no encontrado"));
        }
        couponRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/validate")
    public ResponseEntity<?> validateCode(@RequestBody Map<String, Object> body) {
        String code = body.get("code") != null ? body.get("code").toString().trim() : "";
        Object subtotalValue = body.get("subtotal");
        double subtotal = subtotalValue instanceof Number ? ((Number) subtotalValue).doubleValue() : 0;

        if (code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Ingresá un código de cupón"));
        }

        Coupon coupon = couponRepository.findByCodeIgnoreCase(code).orElse(null);
        if (coupon == null || !coupon.active) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cupón inválido"));
        }
        if (coupon.isExpired()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El cupón expiró"));
        }
        if (!coupon.hasUsesLeft()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El cupón alcanzó su límite de usos"));
        }
        if (subtotal < coupon.minPurchase) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "Este cupón requiere una compra mínima de " + coupon.minPurchase));
        }

        double discount = coupon.computeDiscount(subtotal);
        return ResponseEntity.ok(Map.of("code", coupon.code, "discountAmount", discount));
    }

    private ResponseEntity<?> validate(Coupon coupon) {
        if (coupon.code == null || coupon.code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El código es obligatorio"));
        }
        if (coupon.discountType == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "El tipo de descuento es obligatorio"));
        }
        if (coupon.discountValue <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "El valor del descuento debe ser mayor a 0"));
        }
        if (coupon.discountType == Coupon.DiscountType.PERCENTAGE && coupon.discountValue > 100) {
            return ResponseEntity.badRequest().body(Map.of("error", "Un descuento porcentual no puede superar 100%"));
        }
        if (coupon.minPurchase < 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "La compra mínima no puede ser negativa"));
        }
        if (coupon.maxUses != null && coupon.maxUses < 1) {
            return ResponseEntity.badRequest().body(Map.of("error", "El límite de usos debe ser al menos 1"));
        }
        return null;
    }
}
