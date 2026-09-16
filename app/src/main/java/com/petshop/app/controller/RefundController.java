package com.petshop.app.controller;

import com.petshop.app.model.Product;
import com.petshop.app.model.Refund;
import com.petshop.app.model.Return;
import com.petshop.app.model.User;
import com.petshop.app.repository.ProductRepository;
import com.petshop.app.repository.RefundRepository;
import com.petshop.app.repository.ReturnRepository;
import com.petshop.app.repository.UserRepository;
import com.petshop.app.service.AdminGuard;
import com.petshop.app.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/refunds")
public class RefundController {

    private final RefundRepository refundRepository;
    private final ReturnRepository returnRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AdminGuard adminGuard;

    public RefundController(RefundRepository refundRepository, ReturnRepository returnRepository,
                             ProductRepository productRepository, UserRepository userRepository,
                             NotificationService notificationService, AdminGuard adminGuard) {
        this.refundRepository = refundRepository;
        this.returnRepository = returnRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.adminGuard = adminGuard;
    }

    @PostMapping("/process/{returnId}")
    public ResponseEntity<?> process(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                      @PathVariable Long returnId) {
        if (!adminGuard.isAdmin(token)) {
            return ResponseEntity.status(403).body(Map.of("error", "Requiere permisos de administrador"));
        }

        Return devolucion = returnRepository.findById(returnId).orElse(null);
        if (devolucion == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Devolución no encontrada"));
        }

        if (devolucion.estado != Return.Status.APROBADA) {
            return ResponseEntity.status(409).body(Map.of("error", "Solo se puede reembolsar una devolución aprobada"));
        }

        if (refundRepository.findByReturnId(returnId).isPresent()) {
            return ResponseEntity.status(409).body(Map.of("error", "Esta devolución ya fue reembolsada"));
        }

        Product product = productRepository.findById(devolucion.productId).orElse(null);
        double monto = product != null ? product.price * devolucion.cantidad : 0.0;

        Refund refund = new Refund(devolucion.id, monto, Refund.Status.PROCESADO, Instant.now());
        refundRepository.save(refund);

        devolucion.estado = Return.Status.PROCESADO;
        returnRepository.save(devolucion);

        User user = userRepository.findById(devolucion.userId).orElse(null);
        if (user != null) {
            notificationService.notify(user.email, "Tu reembolso de $" + monto + " fue procesado.");
        }

        return ResponseEntity.ok(refund);
    }
}
