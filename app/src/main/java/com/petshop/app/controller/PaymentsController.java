package com.petshop.app.controller;

import com.petshop.app.payment.mercadopago.MercadoPagoClient;
import com.petshop.app.payment.mercadopago.MercadoPagoClient.MercadoPagoException;
import com.petshop.app.payment.mercadopago.MercadoPagoSettings;
import com.petshop.app.service.VentaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/** Medios de pago disponibles y aviso (webhook) de MercadoPago. */
@RestController
@RequestMapping("/api/payments")
public class PaymentsController {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentsController.class);

    private final MercadoPagoClient mercadoPago;
    private final MercadoPagoSettings settings;
    private final VentaService ventaService;

    public PaymentsController(MercadoPagoClient mercadoPago, MercadoPagoSettings settings, VentaService ventaService) {
        this.mercadoPago = mercadoPago;
        this.settings = settings;
        this.ventaService = ventaService;
    }

    /** Qué medios de pago ofrece la tienda (nunca incluye credenciales). */
    @GetMapping("/config")
    public Map<String, Object> config() {
        Map<String, Object> config = new HashMap<>();
        config.put("mercadoPago", mercadoPago.isEnabled());
        config.put("simulated", settings.simulatedEnabled());
        return config;
    }

    /**
     * Aviso de MercadoPago: "hay novedades del pago X". El aviso solo dice de qué pago hablar: el estado y el monto se le
     * piden a MercadoPago con nuestras credenciales, así que un aviso falso no puede confirmar nada. Siempre se responde
     * 200 si el aviso se entendió (o no corresponde), y 502 si falló la consulta, para que MercadoPago reintente.
     */
    @PostMapping("/mercadopago/webhook")
    public ResponseEntity<?> webhook(@RequestParam Map<String, String> query, @RequestBody(required = false) Map<String, Object> body) {
        if (!mercadoPago.isEnabled()) {
            return ResponseEntity.status(404).build();
        }
        String type = firstNonBlank(query.get("type"), query.get("topic"), body != null ? String.valueOf(body.getOrDefault("type", "")) : null);
        if (!"payment".equalsIgnoreCase(type)) {
            return ResponseEntity.ok(Map.of("ok", true));
        }
        String paymentId = firstNonBlank(query.get("data.id"), query.get("id"), dataId(body));
        if (paymentId == null) {
            return ResponseEntity.ok(Map.of("ok", true));
        }
        try {
            ventaService.procesarAvisoMercadoPago(paymentId);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (MercadoPagoException e) {
            LOG.warn("No se pudo procesar el aviso de MercadoPago por el pago {}: {}", paymentId, e.getMessage());
            return ResponseEntity.status(502).body(Map.of("ok", false));
        }
    }

    @SuppressWarnings("unchecked")
    private static String dataId(Map<String, Object> body) {
        if (body != null && body.get("data") instanceof Map<?, ?> data && data.get("id") != null) {
            return String.valueOf(((Map<String, Object>) data).get("id"));
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank() && !"null".equals(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
