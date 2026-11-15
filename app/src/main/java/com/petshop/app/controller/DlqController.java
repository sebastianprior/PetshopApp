package com.petshop.app.controller;

import com.petshop.app.config.AdminOnly;
import com.petshop.app.service.AdminGuard;
import com.petshop.app.service.DlqService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Cola de mensajes muertos (solo administrador): ver qué hay y reprocesarlo. */
@RestController
@RequestMapping("/api/admin/dlq")
public class DlqController {

    private final DlqService dlq;
    private final AdminGuard adminGuard;

    public DlqController(DlqService dlq, AdminGuard adminGuard) {
        this.dlq = dlq;
        this.adminGuard = adminGuard;
    }

    @AdminOnly
    @GetMapping
    public ResponseEntity<?> listar(@RequestHeader(value = "X-Auth-Token", required = false) String token) {
        if (!adminGuard.isAdmin(token)) {
            return adminGuard.denied(token);
        }
        var letters = dlq.listar();
        return ResponseEntity.ok(Map.of("count", letters.size(), "messages", letters));
    }

    @AdminOnly
    @PostMapping("/reprocess")
    public ResponseEntity<?> reprocesar(@RequestHeader(value = "X-Auth-Token", required = false) String token) {
        if (!adminGuard.isAdmin(token)) {
            return adminGuard.denied(token);
        }
        return ResponseEntity.ok(Map.of("reprocessed", dlq.reprocesar()));
    }
}
