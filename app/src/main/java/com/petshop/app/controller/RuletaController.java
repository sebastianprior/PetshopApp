package com.petshop.app.controller;

import com.petshop.app.model.Category;
import com.petshop.app.service.AdminGuard;
import com.petshop.app.service.JwtUtil;
import com.petshop.app.service.RouletteService;
import com.petshop.app.service.VentaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Ruleta del día: un giro por día y por persona (usuario o invitado), con un 10 % en una categoría sorteada. */
@RestController
@RequestMapping("/api/roulette")
public class RuletaController {

    private final RouletteService rouletteService;
    private final VentaService ventaService;
    private final JwtUtil jwtUtil;
    private final AdminGuard adminGuard;

    public RuletaController(RouletteService rouletteService, VentaService ventaService, JwtUtil jwtUtil, AdminGuard adminGuard) {
        this.rouletteService = rouletteService;
        this.ventaService = ventaService;
        this.jwtUtil = jwtUtil;
        this.adminGuard = adminGuard;
    }

    /** Gajos de la ruleta y el resultado de hoy, si esta persona ya giró. */
    @GetMapping
    public ResponseEntity<?> state(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                   @RequestHeader(value = "X-Guest-Id", required = false) String guestId) {
        String owner = owner(token, guestId);
        if (owner == null) {
            return Problems.unauthorized();
        }
        if (isAdmin(token)) {
            return Problems.forbidden("Los administradores no participan de la ruleta.");
        }
        Optional<Category> today = rouletteService.todaysCategory(owner);
        return ResponseEntity.ok(body(rouletteService.wheel(), today.orElse(null), today.isPresent()));
    }

    @PostMapping("/spin")
    public ResponseEntity<?> spin(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                  @RequestHeader(value = "X-Guest-Id", required = false) String guestId) {
        String owner = owner(token, guestId);
        if (owner == null) {
            return Problems.unauthorized();
        }
        if (isAdmin(token)) {
            return Problems.forbidden("Los administradores no participan de la ruleta.");
        }
        RouletteService.Spin spin;
        try {
            spin = rouletteService.spin(owner);
        } catch (IllegalStateException e) {
            return Problems.of(409, "ruleta-sin-categorias", "Ruleta no disponible", "Todavía no hay categorías para sortear.");
        }
        Map<String, Object> result = body(rouletteService.wheel(), spin.category(), true);
        result.put("alreadySpun", spin.alreadySpun());
        return ResponseEntity.ok(result);
    }

    /** Cuánto descuenta la ruleta sobre el carrito actual (0 si no giró o no hay productos de la categoría). */
    @GetMapping("/cart-discount")
    public ResponseEntity<?> cartDiscount(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                          @RequestHeader(value = "X-Guest-Id", required = false) String guestId) {
        String owner = owner(token, guestId);
        if (owner == null) {
            return Problems.unauthorized();
        }
        RouletteService.Applied applied = rouletteService.discountFor(owner, ventaService.cartOf(owner));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("percent", RouletteService.PERCENT);
        result.put("amount", applied != null ? applied.amount() : 0.0);
        result.put("categoryId", applied != null ? applied.categoryId() : null);
        result.put("categoryName", applied != null ? applied.categoryName() : null);
        return ResponseEntity.ok(result);
    }

    private Map<String, Object> body(List<Category> wheel, Category won, boolean spun) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("percent", RouletteService.PERCENT);
        result.put("categories", wheel.stream().map(c -> Map.of("id", c.id, "name", c.name, "color", c.color == null ? "#FFE6CC" : c.color)).toList());
        result.put("spun", spun);
        result.put("categoryId", won != null ? won.id : null);
        result.put("categoryName", won != null ? won.name : null);
        result.put("validUntil", spun ? rouletteService.validUntil().toString() : null);
        return result;
    }

    private String owner(String token, String guestId) {
        if (token != null && jwtUtil.isTokenValid(token)) {
            return jwtUtil.extractUserId(token);
        }
        return guestId != null && !guestId.isBlank() ? "guest:" + guestId.trim() : null;
    }

    private boolean isAdmin(String token) {
        return token != null && adminGuard.isAdmin(token);
    }
}
