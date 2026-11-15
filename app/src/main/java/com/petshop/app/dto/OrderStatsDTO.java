package com.petshop.app.dto;

import java.util.List;

public class OrderStatsDTO {
    public long totalOrders;
    public double totalRevenue;
    public long ordersToday;
    public List<TopProductStatDTO> topProducts;

    public OrderStatsDTO() {}

    public OrderStatsDTO(long totalOrders, double totalRevenue, long ordersToday, List<TopProductStatDTO> topProducts) {
        this.totalOrders = totalOrders;
        this.totalRevenue = totalRevenue;
        this.ordersToday = ordersToday;
        this.topProducts = topProducts;
    }
}
