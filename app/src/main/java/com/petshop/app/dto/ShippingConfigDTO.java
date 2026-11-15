package com.petshop.app.dto;

import java.util.ArrayList;
import java.util.List;

/** Costos de envío por código postal: las zonas y lo que se cobra al resto del país. */
public class ShippingConfigDTO {

    public static class ZoneDTO {
        public Long id;
        public String name;
        public int postalFrom;
        public int postalTo;
        public double cost;
        public String eta;

        public ZoneDTO() {}

        public ZoneDTO(Long id, String name, int postalFrom, int postalTo, double cost, String eta) {
            this.id = id;
            this.name = name;
            this.postalFrom = postalFrom;
            this.postalTo = postalTo;
            this.cost = cost;
            this.eta = eta;
        }
    }

    /** Costo y plazo para los códigos postales que no están en ninguna zona. */
    public double defaultCost;
    public String defaultEta;
    public List<ZoneDTO> zones = new ArrayList<>();

    public ShippingConfigDTO() {}
}
