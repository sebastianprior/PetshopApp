package com.petshop.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class PaymentConfig {

    @Bean(name = "paymentExecutor", destroyMethod = "shutdownNow")
    public ExecutorService paymentExecutor() {
        return Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "payment-gateway");
            thread.setDaemon(true);
            return thread;
        });
    }
}
