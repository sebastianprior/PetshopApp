package com.petshop.app.service;

import com.petshop.app.model.Product;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class PriceAscStrategy implements ProductSortStrategy {

    @Override
    public List<Product> sort(List<Product> products) {
        products.sort(Comparator.comparingDouble(p -> p.price));
        return products;
    }
}
