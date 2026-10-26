package com.petshop.app.dto;

import java.util.List;

public class SiteSettingsDTO {
    public List<String> topbarItems;
    public String announcementMode;
    public String announcementText;
    /** Desplazar el texto de la barra de derecha a izquierda. Al guardar, nulo = no cambiar. */
    public Boolean topbarMarquee;
    public Boolean announcementMarquee;

    public SiteSettingsDTO() {}

    public SiteSettingsDTO(List<String> topbarItems, String announcementMode, String announcementText) {
        this.topbarItems = topbarItems;
        this.announcementMode = announcementMode;
        this.announcementText = announcementText;
    }
}
