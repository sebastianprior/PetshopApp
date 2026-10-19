package com.petshop.app.dto;

import java.util.List;

public class SiteSettingsDTO {
    public List<String> topbarItems;
    public String announcementMode;
    public String announcementText;

    public SiteSettingsDTO() {}

    public SiteSettingsDTO(List<String> topbarItems, String announcementMode, String announcementText) {
        this.topbarItems = topbarItems;
        this.announcementMode = announcementMode;
        this.announcementText = announcementText;
    }
}
