package com.petshop.app.service;

import com.petshop.app.model.Product;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class PriceDescStrategy implements ProductSortStrategy {

    @Override
    public List<Product> sort(List<Product> products) {
        products.sort(Comparator.comparingDouble((Product p) -> p.price).reversed());
        return products;
    }
}
