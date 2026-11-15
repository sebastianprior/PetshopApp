package com.petshop.app.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PendingPaymentWorker {

    private final VentaService ventaService;

    public PendingPaymentWorker(VentaService ventaService) {
        this.ventaService = ventaService;
    }

    @Scheduled(fixedDelayString = "${petshop.payments.worker-delay-ms:15000}",
            initialDelayString = "${petshop.payments.worker-delay-ms:15000}")
    public void procesar() {
        ventaService.procesarPendientes();
    }
}
