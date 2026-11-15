package com.petshop.app.dto;

public class TopProductStatDTO {
    public String productId;
    public String name;
    public long totalQuantity;

    public TopProductStatDTO() {}

    public TopProductStatDTO(String productId, String name, long totalQuantity) {
        this.productId = productId;
        this.name = name;
        this.totalQuantity = totalQuantity;
    }
}
