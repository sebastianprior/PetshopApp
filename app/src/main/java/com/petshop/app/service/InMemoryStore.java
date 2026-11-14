package com.petshop.app.service;

import com.petshop.app.model.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryStore {
    public final List<Category> categories = Collections.synchronizedList(new ArrayList<>());
    public final List<Product> products = Collections.synchronizedList(new ArrayList<>());
    public final Map<String, List<CartItem>> carts = new ConcurrentHashMap<>();
    public final Map<String, User> users = new ConcurrentHashMap<>();
    public final Map<String, com.petshop.app.model.ResetToken> resetTokens = new ConcurrentHashMap<>(); // token -> ResetToken

    public Optional<Product> findProduct(String id) {
        return products.stream().filter(p -> p.id.equals(id)).findFirst();
    }
}
