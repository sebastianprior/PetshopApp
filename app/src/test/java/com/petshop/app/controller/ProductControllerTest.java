package com.petshop.app.controller;

import com.petshop.app.model.Product;
import com.petshop.app.model.ProductVariant;
import com.petshop.app.repository.ProductRepository;
import com.petshop.app.repository.ProductVariantRepository;
import com.petshop.app.service.AdminGuard;
import com.petshop.app.service.JwtUtil;
import com.petshop.app.service.PriceAscStrategy;
import com.petshop.app.service.PriceDescStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductControllerTest {

    private ProductRepository products;
    private ProductVariantRepository variants;
    private ProductController controller;
    private String adminToken;
    private String customerToken;

    private static Product product(String id, String name, String brand, double price, String category) {
        return new Product(id, name, brand, price, null, 4.0, "/products/" + id + ".svg", null, category, 10);
    }

    @BeforeEach
    void setUp() {
        products = mock(ProductRepository.class);
        variants = mock(ProductVariantRepository.class);
        JwtUtil jwtUtil = new JwtUtil("test-secret-test-secret-test-secret-test-secret", 60_000);
        controller = new ProductController(products, variants, new PriceAscStrategy(), new PriceDescStrategy(), new AdminGuard(jwtUtil));
        adminToken = jwtUtil.generateToken("admin-1", "admin@example.com", "ADMIN");
        customerToken = jwtUtil.generateToken("user-1", "cliente@example.com", "CUSTOMER");
    }

    // ---- Listado ----------------------------------------------------------------------------------------------------

    @Test
    void listsEveryProductWhenThereIsNoCategory() {
        when(products.findAll()).thenReturn(List.of(product("p1", "Collar", "WalkEasy", 899, "accesorios")));

        assertThat(controller.list(null, null, null)).extracting(p -> p.id).containsExactly("p1");
        assertThat(controller.list("  ", null, null)).hasSize(1);
        verify(products, never()).findByCategoryIdIgnoreCase(any());
    }

    @Test
    void filtersByCategory() {
        when(products.findByCategoryIdIgnoreCase("juguetes")).thenReturn(List.of(product("p2", "Pelota", "PetPlay", 499, "juguetes")));

        assertThat(controller.list("juguetes", null, null)).extracting(p -> p.id).containsExactly("p2");
    }

    @Test
    void searchMatchesNameOrBrandIgnoringCaseAndSpaces() {
        when(products.findAll()).thenReturn(List.of(
                product("p1", "Collar Comfort", "WalkEasy", 899, "accesorios"),
                product("p2", "Pelota", "PetPlay", 499, "juguetes"),
                product("p3", "Arnés", "WALKfit", 1599, "accesorios")));

        assertThat(controller.list(null, null, "  collar ")).extracting(p -> p.id).containsExactly("p1");
        assertThat(controller.list(null, null, "walk")).extracting(p -> p.id).containsExactly("p1", "p3");
        assertThat(controller.list(null, null, "no-existe")).isEmpty();
    }

    @Test
    void sortsByPriceAscendingAndDescending() {
        // Como la base de datos real, cada consulta devuelve una lista nueva y modificable
        when(products.findAll()).thenAnswer(inv -> new ArrayList<>(List.of(
                product("caro", "A", "x", 3000, "c"), product("barato", "B", "x", 100, "c"), product("medio", "C", "x", 900, "c"))));

        assertThat(controller.list(null, "Menor precio", null)).extracting(p -> p.id).containsExactly("barato", "medio", "caro");
        assertThat(controller.list(null, "menorprecio", null)).extracting(p -> p.id).containsExactly("barato", "medio", "caro");
        assertThat(controller.list(null, "Mayor precio", null)).extracting(p -> p.id).containsExactly("caro", "medio", "barato");
        assertThat(controller.list(null, "MAYORPRECIO", null)).extracting(p -> p.id).containsExactly("caro", "medio", "barato");
    }

    @Test
    void anUnknownSortKeepsTheOriginalOrder() {
        when(products.findAll()).thenReturn(List.of(product("b", "B", "x", 500, "c"), product("a", "A", "x", 100, "c")));

        assertThat(controller.list(null, "popularidad", null)).extracting(p -> p.id).containsExactly("b", "a");
    }

    @Test
    void marksWhichProductsHaveVariants() {
        when(products.findAll()).thenReturn(List.of(product("p1", "Collar", "x", 1, "c"), product("p2", "Pelota", "x", 1, "c")));
        when(variants.findByProductIdIn(List.of("p1", "p2"))).thenReturn(List.of(new ProductVariant("p1", "L", "rojo", 3)));

        List<Product> result = controller.list(null, null, null);

        assertThat(result.get(0).hasVariants).isTrue();
        assertThat(result.get(1).hasVariants).isFalse();
    }

    @Test
    void doesNotQueryVariantsWhenThereAreNoProducts() {
        when(products.findAll()).thenReturn(List.of());

        assertThat(controller.list(null, null, null)).isEmpty();
        verify(variants, never()).findByProductIdIn(any());
    }

    // ---- Detalle ----------------------------------------------------------------------------------------------------

    @Test
    void returns404ForAProductThatDoesNotExist() {
        when(products.findById("nada")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.get("nada");

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isEqualTo(Map.of("error", "Producto no encontrado"));
    }

    @Test
    void theDetailSaysWhetherTheProductHasVariants() {
        Product collar = product("p1", "Collar", "x", 1, "c");
        when(products.findById("p1")).thenReturn(Optional.of(collar));
        when(variants.findByProductId("p1")).thenReturn(List.of(new ProductVariant("p1", "M", "azul", 2)));

        ResponseEntity<?> response = controller.get("p1");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(((Product) response.getBody()).hasVariants).isTrue();
    }

    // ---- Alta, cambio y baja: solo administrador ----------------------------------------------------------------------

    @Test
    void onlyAnAdministratorCanCreateUpdateOrDelete() {
        Product body = product("p1", "Collar", "x", 1, "c");

        // Sin una sesión válida es 401; con la sesión de un cliente, 403
        for (String token : new String[]{null, "", "token-roto", customerToken}) {
            int expected = customerToken.equals(token) ? 403 : 401;
            assertThat(controller.create(token, body).getStatusCode().value()).isEqualTo(expected);
            assertThat(controller.update(token, null, "p1", body).getStatusCode().value()).isEqualTo(expected);
            assertThat(controller.delete(token, "p1").getStatusCode().value()).isEqualTo(expected);
        }
        verify(products, never()).save(any());
        verify(products, never()).deleteById(any());
    }

    @Test
    void createGeneratesAnIdWhenNoneIsGivenAndKeepsAGivenOne() {
        Product withoutId = product("", "Nuevo", "x", 10, "c");
        Product withId = product("mi-id", "Otro", "x", 10, "c");

        ResponseEntity<?> first = controller.create(adminToken, withoutId);
        ResponseEntity<?> second = controller.create(adminToken, withId);

        assertThat(first.getStatusCode().value()).isEqualTo(200);
        assertThat(withoutId.id).isNotBlank().hasSize(36);
        assertThat(second.getStatusCode().value()).isEqualTo(200);
        assertThat(withId.id).isEqualTo("mi-id");
        verify(products).save(withoutId);
        verify(products).save(withId);
    }

    @Test
    void updateUsesTheIdFromTheAddressNotTheOneInTheBody() {
        when(products.findById("p1")).thenReturn(Optional.of(product("p1", "Collar", "x", 1, "c")));
        Product body = product("otro-id", "Cambiado", "x", 99, "c");

        ResponseEntity<?> response = controller.update(adminToken, null, "p1", body);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(body.id).isEqualTo("p1");
        verify(products).save(body);
    }

    @Test
    void updateOfAMissingProductIsRejected() {
        when(products.findById("p1")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.update(adminToken, null, "p1", product("p1", "x", "x", 1, "c"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        verify(products, never()).save(any());
    }

    // ---- ETag / If-Match: no pisar el cambio de otra persona --------------------------------------------------------

    private Product stored;

    private String readETag() {
        when(products.findById("p1")).thenAnswer(inv -> Optional.of(stored));
        return controller.get("p1").getHeaders().getETag();
    }

    @Test
    void readingAProductGivesItsETagAndItChangesWithTheData() {
        stored = product("p1", "Collar", "x", 899, "c");
        String before = readETag();
        assertThat(before).isNotBlank().startsWith("\"");
        assertThat(readETag()).isEqualTo(before);

        stored.price = 999;

        assertThat(readETag()).isNotEqualTo(before);
    }

    @Test
    void aStaleETagRejectsTheUpdateWith412AndSavesNothing() {
        stored = product("p1", "Collar", "x", 899, "c");
        String mine = readETag();
        stored.stock = 3; // otro administrador lo modificó después de que yo lo leí

        ResponseEntity<?> response = controller.update(adminToken, mine, "p1", product("p1", "Collar", "x", 1000, "c"));

        assertThat(response.getStatusCode().value()).isEqualTo(412);
        assertThat(response.getBody()).isInstanceOf(org.springframework.http.ProblemDetail.class);
        assertThat(((org.springframework.http.ProblemDetail) response.getBody()).getDetail()).contains("ETag desactualizado");
        verify(products, never()).save(any());
    }

    @Test
    void aCurrentETagAllowsTheUpdateAndReturnsTheNewOne() {
        stored = product("p1", "Collar", "x", 899, "c");
        String mine = readETag();
        Product body = product("p1", "Collar", "x", 1000, "c");

        ResponseEntity<?> response = controller.update(adminToken, mine, "p1", body);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getETag()).isNotEqualTo(mine);
        verify(products).save(body);
    }

    @Test
    void ifMatchWithSeveralValuesOrAWeakPrefixIsUnderstood() {
        stored = product("p1", "Collar", "x", 899, "c");
        String mine = readETag();

        assertThat(controller.update(adminToken, "\"otro\", W/" + mine, "p1", product("p1", "A", "x", 1, "c")).getStatusCode().value()).isEqualTo(200);
        assertThat(controller.update(adminToken, "*", "p1", product("p1", "B", "x", 1, "c")).getStatusCode().value()).isEqualTo(200);
        assertThat(controller.update(adminToken, "\"otro\"", "p1", product("p1", "C", "x", 1, "c")).getStatusCode().value()).isEqualTo(412);
    }

    @Test
    void deleteRemovesTheVariantsBeforeTheProduct() {
        when(products.existsById("p1")).thenReturn(true);
        List<ProductVariant> theVariants = List.of(new ProductVariant("p1", "L", "rojo", 1));
        when(variants.findByProductId("p1")).thenReturn(theVariants);

        ResponseEntity<?> response = controller.delete(adminToken, "p1");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(Map.of("ok", true));
        InOrder order = inOrder(variants, products);
        order.verify(variants).deleteAll(theVariants);
        order.verify(products).deleteById("p1");
    }

    @Test
    void deleteOfAMissingProductIsRejectedAndNothingIsRemoved() {
        when(products.existsById("p1")).thenReturn(false);

        ResponseEntity<?> response = controller.delete(adminToken, "p1");

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        verify(products, never()).deleteById(any());
        verify(variants, never()).deleteAll(any());
    }
}
