package com.petshop.app.jakarta;

import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.context.annotation.Configuration;

/** Aplicación Jakarta REST (JAX-RS / Jersey) publicada en /jakarta/*, junto a la API de Spring MVC. */
@Configuration
public class JakartaRestConfig extends ResourceConfig {

    public JakartaRestConfig() {
        register(CatalogoYPagoResource.class);
    }
}
