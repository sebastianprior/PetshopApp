package com.petshop.app.controller;

import com.petshop.app.model.Product;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Catálogo público de la API v1; reutiliza la lógica de filtros y orden de ProductController. */
@RestController
@RequestMapping("/api/v1/productos")
public class ProductoV1Controller {

    private final ProductController productController;

    public ProductoV1Controller(ProductController productController) {
        this.productController = productController;
    }

    @GetMapping
    public List<Product> listar(@RequestParam(required = false) String category,
                                @RequestParam(required = false) String sort,
                                @RequestParam(required = false) String search) {
        return productController.list(category, sort, search);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtener(@PathVariable String id) {
        ResponseEntity<?> response = productController.get(id);
        if (response.getStatusCode().value() == 404) {
            return Problems.notFound("No existe el producto " + id + ".");
        }
        return response;
    }
}
