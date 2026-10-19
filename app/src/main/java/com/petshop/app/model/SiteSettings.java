package com.petshop.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Configuración del sitio editable desde el panel de administración (una sola fila, id = 1). */
@Entity
@Table(name = "site_settings")
public class SiteSettings {

    public static final long SINGLETON_ID = 1L;

    @Id
    public Long id = SINGLETON_ID;

    /** Textos de la barra superior, uno por línea. */
    @Column(length = 1000)
    public String topbarItems;

    /** AUTO (ofertas de los productos), CUSTOM (texto propio) u OFF (sin barra). */
    @Column(length = 10)
    public String announcementMode;

    @Column(length = 300)
    public String announcementText;

    public SiteSettings() {}
}
