package com.petshop.app.service;

import com.petshop.app.model.Product;

import java.util.List;

public interface ProductSortStrategy {
    List<Product> sort(List<Product> products);
}
