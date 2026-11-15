package com.petshop.app.service;

import com.petshop.app.model.Category;
import com.petshop.app.model.Product;
import com.petshop.app.model.User;
import com.petshop.app.repository.CategoryRepository;
import com.petshop.app.repository.ProductRepository;
import com.petshop.app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Los datos de ejemplo se cargan una sola vez, sin duplicar ni pisar lo que ya existe, y las contraseñas se guardan con hash. */
class DatabaseSeederTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private CategoryRepository categories;
    private ProductRepository products;
    private UserRepository users;
    private DatabaseSeeder seeder;

    @BeforeEach
    void setUp() {
        categories = mock(CategoryRepository.class);
        products = mock(ProductRepository.class);
        users = mock(UserRepository.class);
        seeder = new DatabaseSeeder(categories, products, users, "admin123");
    }

    @Test
    void loadsTheCatalogAndTheDevelopmentUsersWhenTheDatabaseIsEmpty() {
        seeder.run(null);

        verify(categories, times(7)).save(any(Category.class));
        ArgumentCaptor<Product> savedProducts = ArgumentCaptor.forClass(Product.class);
        verify(products, times(50)).save(savedProducts.capture());
        assertThat(savedProducts.getAllValues()).extracting(p -> p.id).doesNotHaveDuplicates();
        assertThat(savedProducts.getAllValues()).allSatisfy(p -> {
            assertThat(p.imageUrl).isEqualTo("/products/" + p.id + ".svg");
            assertThat(p.price).isPositive();
            assertThat(p.stock).isPositive();
        });

        ArgumentCaptor<User> savedUsers = ArgumentCaptor.forClass(User.class);
        verify(users, times(2)).save(savedUsers.capture());
        assertThat(savedUsers.getAllValues()).extracting(u -> u.email)
                .containsExactlyInAnyOrder("cliente@ejemplo.com", "admin@petshop.com");
    }

    @Test
    void storesPasswordsHashedAndGivesTheAdminRoleOnlyToTheAdmin() {
        seeder.run(null);

        ArgumentCaptor<User> savedUsers = ArgumentCaptor.forClass(User.class);
        verify(users, times(2)).save(savedUsers.capture());
        User admin = savedUsers.getAllValues().stream().filter(u -> u.email.equals("admin@petshop.com")).findFirst().orElseThrow();
        User customer = savedUsers.getAllValues().stream().filter(u -> u.email.equals("cliente@ejemplo.com")).findFirst().orElseThrow();

        assertThat(admin.role).isEqualTo("ADMIN");
        assertThat(customer.role).isEqualTo("CUSTOMER");
        assertThat(savedUsers.getAllValues()).allSatisfy(u -> assertThat(u.password).startsWith("$2"));
        assertThat(encoder.matches("admin123", admin.password)).isTrue();
        assertThat(encoder.matches("password", customer.password)).isTrue();
        assertThat(encoder.matches("otra", admin.password)).isFalse();
    }

    @Test
    void doesNotTouchAnythingWhenTheDataIsAlreadyLoaded() {
        when(categories.existsById(anyString())).thenReturn(true);
        when(products.existsById(anyString())).thenReturn(true);
        when(products.findById(anyString())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            return Optional.of(new Product(id, "x", "x", 1.0, null, 4.0, "/products/" + id + ".svg", null, "perros", 1));
        });
        when(users.findByEmail(anyString())).thenAnswer(inv -> {
            String email = inv.getArgument(0);
            User existing = new User("u", email, encoder.encode("lo-que-sea"), "x");
            existing.role = email.equals("admin@petshop.com") ? "ADMIN" : "CUSTOMER";
            return Optional.of(existing);
        });

        seeder.run(null);

        verify(categories, never()).save(any());
        verify(products, never()).save(any());
        verify(users, never()).save(any());
    }

    @Test
    void refreshesAProductWhoseImageIsStale() {
        when(products.existsById(anyString())).thenReturn(false);
        when(products.existsById("p1")).thenReturn(true);
        Product stale = new Product("p1", "Viejo", "Marca", 1.0, null, 1.0, "/images/viejo.png", null, "perros", 1);
        when(products.findById("p1")).thenReturn(Optional.of(stale));

        seeder.run(null);

        verify(products).save(stale);
        assertThat(stale.imageUrl).isEqualTo("/products/p1.svg");
        assertThat(stale.name).isEqualTo("Croquetas SuperDog 10kg");
        assertThat(stale.price).isEqualTo(12999.0);
    }

    @Test
    void hashesAPasswordThatWasStoredInPlainText() {
        User legacy = new User("u1", "cliente@ejemplo.com", "password", "Cliente Demo");
        when(users.findByEmail("cliente@ejemplo.com")).thenReturn(Optional.of(legacy));

        seeder.run(null);

        verify(users).save(legacy);
        assertThat(legacy.password).startsWith("$2");
        assertThat(encoder.matches("password", legacy.password)).isTrue();
    }

    @Test
    void restoresTheRoleOfASeededUser() {
        User demoted = new User("u2", "admin@petshop.com", encoder.encode("admin123"), "Administrador");
        demoted.role = "CUSTOMER";
        when(users.findByEmail("admin@petshop.com")).thenReturn(Optional.of(demoted));

        seeder.run(null);

        verify(users).save(demoted);
        assertThat(demoted.role).isEqualTo("ADMIN");
    }

    @Test
    void thereIsExactlyOneAdminAmongTheSeededUsers() {
        seeder.run(null);

        ArgumentCaptor<User> savedUsers = ArgumentCaptor.forClass(User.class);
        verify(users, times(2)).save(savedUsers.capture());
        List<User> admins = savedUsers.getAllValues().stream().filter(u -> "ADMIN".equals(u.role)).toList();
        assertThat(admins).hasSize(1);
    }
}
