package com.petshop.app.controller;

import com.petshop.app.dto.ChatRequest;
import com.petshop.app.dto.ChatResponse;
import com.petshop.app.model.Product;
import com.petshop.app.repository.FaqItemRepository;
import com.petshop.app.repository.ProductRepository;
import com.petshop.app.repository.ProductVariantRepository;
import com.petshop.app.service.ChatAdvisor;
import com.petshop.app.service.FaqMatcher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Salchi Bot: recomienda productos del catálogo a partir de lo que escribe el cliente. Es público y de solo lectura. */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final int MAX_HISTORY = 4;

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final FaqItemRepository faqRepository;

    public ChatController(ProductRepository productRepository, ProductVariantRepository variantRepository, FaqItemRepository faqRepository) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.faqRepository = faqRepository;
    }

    @PostMapping
    public ResponseEntity<?> chat(@RequestBody ChatRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Falta el mensaje"));
        }

        List<String> history = new ArrayList<>();
        if (request.history != null) {
            int from = Math.max(0, request.history.size() - MAX_HISTORY);
            request.history.subList(from, request.history.size()).stream().filter(m -> m != null).forEach(history::add);
        }

        List<Product> catalog = productRepository.findAll();
        List<FaqMatcher.Entry> faq = faqRepository.findAllByOrderByPositionAscIdAsc().stream()
                .map(item -> new FaqMatcher.Entry(item.question, item.answer)).toList();
        ChatAdvisor.Advice advice = new ChatAdvisor(catalog, faq).advise(request.message, history);

        Map<String, Product> byId = catalog.stream().collect(Collectors.toMap(p -> p.id, p -> p, (a, b) -> a));
        List<Product> products = advice.productIds().stream().map(byId::get).filter(p -> p != null).toList();
        markVariants(products);
        return ResponseEntity.ok(new ChatResponse(advice.reply(), products, advice.suggestions()));
    }

    private void markVariants(List<Product> products) {
        if (products.isEmpty()) {
            return;
        }
        Set<String> withVariants = variantRepository.findByProductIdIn(products.stream().map(p -> p.id).toList()).stream()
                .map(v -> v.productId)
                .collect(Collectors.toSet());
        products.forEach(p -> p.hasVariants = withVariants.contains(p.id));
    }
}
