package com.petshop.app.controller;

import com.petshop.app.model.Category;
import com.petshop.app.repository.CategoryRepository;
import com.petshop.app.service.AdminGuard;
import com.petshop.app.service.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CategoryControllerTest {

    private CategoryRepository categories;
    private CategoryController controller;
    private String adminToken;
    private String customerToken;

    @BeforeEach
    void setUp() {
        categories = mock(CategoryRepository.class);
        JwtUtil jwtUtil = new JwtUtil("test-secret-test-secret-test-secret-test-secret", 60_000);
        controller = new CategoryController(categories, new AdminGuard(jwtUtil));
        adminToken = jwtUtil.generateToken("admin-1", "admin@example.com", "ADMIN");
        customerToken = jwtUtil.generateToken("user-1", "cliente@example.com", "CUSTOMER");
    }

    @Test
    void anyoneCanListTheCategories() {
        List<Category> all = List.of(new Category("perros", "Perros", "#FFD6A5"));
        when(categories.findAll()).thenReturn(all);

        assertThat(controller.list()).isEqualTo(all);
    }

    @Test
    void onlyAnAdministratorCanChangeCategories() {
        Category body = new Category("perros", "Perros", "#FFD6A5");

        // Sin una sesión válida es 401; con la sesión de un cliente, 403
        for (String token : new String[]{null, "token-roto", customerToken}) {
            int expected = customerToken.equals(token) ? 403 : 401;
            assertThat(controller.create(token, body).getStatusCode().value()).isEqualTo(expected);
            assertThat(controller.update(token, "perros", body).getStatusCode().value()).isEqualTo(expected);
            assertThat(controller.delete(token, "perros").getStatusCode().value()).isEqualTo(expected);
        }
        verify(categories, never()).save(any());
        verify(categories, never()).deleteById(any());
    }

    @Test
    void createGeneratesAnIdWhenMissing() {
        Category body = new Category("", "Aves", "#fff");

        ResponseEntity<?> response = controller.create(adminToken, body);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(body.id).hasSize(36);
        verify(categories).save(body);
    }

    @Test
    void aCategoryWithoutANameIsRejectedOnCreateAndUpdate() {
        when(categories.existsById("perros")).thenReturn(true);
        Category noName = new Category("x", "x", "#fff");
        noName.name = null;

        for (Category bad : new Category[]{noName, new Category("x", "", "#fff"), new Category("x", "   ", "#fff")}) {
            assertThat(controller.create(adminToken, bad).getStatusCode().value()).isEqualTo(400);
            assertThat(controller.update(adminToken, "perros", bad).getStatusCode().value()).isEqualTo(400);
        }
        verify(categories, never()).save(any());
    }

    @Test
    void createKeepsTheGivenId() {
        Category body = new Category("aves", "Aves", "#fff");

        controller.create(adminToken, body);

        assertThat(body.id).isEqualTo("aves");
        verify(categories).save(body);
    }

    @Test
    void updateUsesTheIdFromTheAddress() {
        when(categories.existsById("perros")).thenReturn(true);
        Category body = new Category("otro", "Perros y cachorros", "#000");

        ResponseEntity<?> response = controller.update(adminToken, "perros", body);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(body.id).isEqualTo("perros");
        verify(categories).save(body);
    }

    @Test
    void updateAndDeleteOfAMissingCategoryAreRejected() {
        when(categories.existsById("nada")).thenReturn(false);

        ResponseEntity<?> update = controller.update(adminToken, "nada", new Category("nada", "x", "#fff"));
        ResponseEntity<?> delete = controller.delete(adminToken, "nada");

        assertThat(update.getStatusCode().value()).isEqualTo(400);
        assertThat(update.getBody()).isEqualTo(Map.of("error", "Categoría no encontrada"));
        assertThat(delete.getStatusCode().value()).isEqualTo(400);
        verify(categories, never()).save(any());
        verify(categories, never()).deleteById(any());
    }

    @Test
    void deleteRemovesAnExistingCategory() {
        when(categories.existsById("perros")).thenReturn(true);

        ResponseEntity<?> response = controller.delete(adminToken, "perros");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(Map.of("ok", true));
        verify(categories).deleteById("perros");
    }
}
