package com.petshop.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class PaymentConfig {

    /**
     * Hilos para llamar a la pasarela de pagos. Tienen un tope: si la pasarela se pone lenta y se llena, las llamadas
     * nuevas se rechazan en el momento (la venta queda pendiente y se reintenta) en vez de acumular hilos sin límite.
     */
    @Bean(name = "paymentExecutor", destroyMethod = "shutdownNow")
    public ExecutorService paymentExecutor(@Value("${petshop.payments.max-concurrent-calls:32}") int maxConcurrentCalls) {
        return new ThreadPoolExecutor(0, maxConcurrentCalls, 30, TimeUnit.SECONDS, new SynchronousQueue<>(), runnable -> {
            Thread thread = new Thread(runnable, "payment-gateway");
            thread.setDaemon(true);
            return thread;
        });
    }
}
