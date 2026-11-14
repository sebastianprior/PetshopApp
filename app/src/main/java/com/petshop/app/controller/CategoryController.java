package com.petshop.app.controller;

import com.petshop.app.model.Category;
import com.petshop.app.repository.CategoryRepository;
import com.petshop.app.service.AdminGuard;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.petshop.app.config.AdminOnly;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryRepository categoryRepository;
    private final AdminGuard adminGuard;

    public CategoryController(CategoryRepository categoryRepository, AdminGuard adminGuard) {
        this.categoryRepository = categoryRepository;
        this.adminGuard = adminGuard;
    }

    @GetMapping
    public List<Category> list() {
        return categoryRepository.findAll();
    }

    @AdminOnly
    @PostMapping
    public ResponseEntity<?> create(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                     @RequestBody Category category) {
        if (!adminGuard.isAdmin(token)) {
            return adminGuard.denied(token);
        }

        if (category.name == null || category.name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El nombre es obligatorio"));
        }
        if (category.id == null || category.id.isBlank()) {
            category.id = UUID.randomUUID().toString();
        }
        categoryRepository.save(category);
        return ResponseEntity.ok(category);
    }

    @AdminOnly
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                     @PathVariable String id,
                                     @RequestBody Category category) {
        if (!adminGuard.isAdmin(token)) {
            return adminGuard.denied(token);
        }

        if (!categoryRepository.existsById(id)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Categoría no encontrada"));
        }

        if (category.name == null || category.name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El nombre es obligatorio"));
        }
        category.id = id;
        categoryRepository.save(category);
        return ResponseEntity.ok(category);
    }

    @AdminOnly
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                     @PathVariable String id) {
        if (!adminGuard.isAdmin(token)) {
            return adminGuard.denied(token);
        }

        if (!categoryRepository.existsById(id)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Categoría no encontrada"));
        }

        categoryRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
