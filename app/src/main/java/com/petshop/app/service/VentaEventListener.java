package com.petshop.app.service;

import com.petshop.app.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class VentaEventListener {

    private static final Logger LOG = LoggerFactory.getLogger(VentaEventListener.class);

    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public VentaEventListener(UserRepository userRepository, NotificationService notificationService) {
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Async
    @EventListener
    public void onVentaConfirmada(VentaEvents.VentaConfirmada event) {
        LOG.info("[EVENTO] VentaConfirmada ventaId={} total={}", event.ventaId(), event.total());
        userRepository.findById(event.userId()).ifPresent(user ->
                notificationService.notify(user.email,
                        "Tu compra #" + event.ventaId() + " fue confirmada. Total: $" + event.total()));
    }

    @Async
    @EventListener
    public void onVentaCancelada(VentaEvents.VentaCancelada event) {
        LOG.info("[EVENTO] VentaCancelada ventaId={} motivo={}", event.ventaId(), event.motivo());
        userRepository.findById(event.userId()).ifPresent(user ->
                notificationService.notify(user.email,
                        "Tu compra #" + event.ventaId() + " fue cancelada: " + event.motivo()));
    }
}
