package com.petshop.app.dto;

import com.petshop.app.model.Product;

import java.util.List;

public class ChatResponse {
    public String reply;
    public List<Product> products;
    public List<String> suggestions;

    public ChatResponse() {}

    public ChatResponse(String reply, List<Product> products, List<String> suggestions) {
        this.reply = reply;
        this.products = products;
        this.suggestions = suggestions;
    }
}
