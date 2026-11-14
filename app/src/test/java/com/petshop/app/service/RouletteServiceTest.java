package com.petshop.app.service;

import com.petshop.app.model.CartItem;
import com.petshop.app.model.Category;
import com.petshop.app.model.Product;
import com.petshop.app.model.RouletteSpin;
import com.petshop.app.repository.CategoryRepository;
import com.petshop.app.repository.ProductRepository;
import com.petshop.app.repository.RouletteSpinRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RouletteServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    private final Map<String, RouletteSpin> saved = new HashMap<>();
    private RouletteSpinRepository spins;
    private ProductRepository products;
    private RouletteService service;
    private Instant now = Instant.parse("2026-10-19T15:00:00Z"); // 12:00 en Buenos Aires
    private int nextPick = 2;

    @BeforeEach
    void setUp() {
        spins = mock(RouletteSpinRepository.class);
        when(spins.findByOwnerKeyAndDay(anyString(), any(LocalDate.class))).thenAnswer(inv ->
                Optional.ofNullable(saved.get(inv.getArgument(0) + "|" + inv.getArgument(1))));
        when(spins.save(any(RouletteSpin.class))).thenAnswer(inv -> {
            RouletteSpin spin = inv.getArgument(0);
            saved.put(spin.ownerKey + "|" + spin.day, spin);
            return spin;
        });

        CategoryRepository categories = mock(CategoryRepository.class);
        List<Category> all = List.of(new Category("perros", "Perros", "#FFD6A5"), new Category("gatos", "Gatos", "#BFEAF5"),
                new Category("juguetes", "Juguetes", "#FDE2E4"), new Category("higiene", "Higiene", "#D1FAE5"));
        when(categories.findAll()).thenReturn(new ArrayList<>(all.reversed()));
        when(categories.findById(anyString())).thenAnswer(inv -> all.stream().filter(c -> c.id.equals(inv.getArgument(0))).findFirst());

        products = mock(ProductRepository.class);
        Product ball = new Product("p2", "Pelota", "PetPlay", 500, null, 4.3, null, null, "juguetes", 10);
        when(products.findById("p2")).thenReturn(Optional.of(ball));
        when(products.findById("p14")).thenReturn(Optional.of(new Product("p14", "Shampoo", "FreshPaw", 1300, null, 4.7, null, null, "higiene", 10)));
        when(products.findByCategoryIdIgnoreCase(anyString())).thenReturn(List.of(ball)); // todas las categorías tienen productos

        Random random = new Random() {
            @Override
            public int nextInt(int bound) {
                return nextPick % bound;
            }
        };
        service = new RouletteService(spins, categories, products, new Clock() {
            @Override
            public ZoneId getZone() {
                return ZONE;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now;
            }
        }, random, ZONE);
    }

    private static CartItem item(String productId, int quantity, double price) {
        return new CartItem(productId, "x", "x", quantity, price);
    }

    @Test
    void theWheelHasAFixedOrderSoEveryoneSeesTheSameSegments() {
        assertThat(service.wheel()).extracting(c -> c.id).containsExactly("gatos", "higiene", "juguetes", "perros");
    }

    @Test
    void categoriesWithoutProductsInStockAreNotOnTheWheel() {
        when(products.findByCategoryIdIgnoreCase("perros")).thenReturn(List.of());
        Product soldOut = new Product("p9", "Agotado", "X", 100, null, 4.0, null, null, "gatos", 0);
        when(products.findByCategoryIdIgnoreCase("gatos")).thenReturn(List.of(soldOut));

        assertThat(service.wheel()).extracting(c -> c.id).containsExactly("higiene", "juguetes");
    }

    @Test
    void theFirstSpinOfTheDayDrawsACategoryAndSavesIt() {
        RouletteService.Spin spin = service.spin("user-1");

        assertThat(spin.alreadySpun()).isFalse();
        assertThat(spin.category().id).isEqualTo("juguetes"); // índice 2 del orden fijo
        assertThat(saved).containsKey("user-1|2026-10-19");
    }

    @Test
    void spinningAgainTheSameDayReturnsTheSameResultWithoutDrawingAgain() {
        service.spin("user-1");
        nextPick = 0; // aunque el azar diera otra cosa

        RouletteService.Spin again = service.spin("user-1");

        assertThat(again.alreadySpun()).isTrue();
        assertThat(again.category().id).isEqualTo("juguetes");
        verify(spins, org.mockito.Mockito.times(1)).save(any());
    }

    @Test
    void everyPersonAndEveryDayHasTheirOwnSpin() {
        service.spin("user-1");
        nextPick = 3;
        assertThat(service.spin("guest:abc").category().id).isEqualTo("perros");

        now = Instant.parse("2026-10-20T15:00:00Z");
        nextPick = 1;
        RouletteService.Spin nextDay = service.spin("user-1");

        assertThat(nextDay.alreadySpun()).isFalse();
        assertThat(nextDay.category().id).isEqualTo("higiene");
    }

    @Test
    void theDiscountExpiresAtMidnightInTheStoreTimeZone() {
        // 12:00 en Buenos Aires (UTC-3) -> vence a las 00:00 locales = 03:00 UTC del día siguiente
        assertThat(service.validUntil()).isEqualTo(Instant.parse("2026-10-20T03:00:00Z"));
    }

    @Test
    void aRacedDoubleSpinKeepsTheFirstOneSaved() {
        RouletteSpin first = new RouletteSpin("user-1", LocalDate.parse("2026-10-19"), "gatos");
        when(spins.findByOwnerKeyAndDay("user-1", LocalDate.parse("2026-10-19")))
                .thenReturn(Optional.empty()).thenReturn(Optional.of(first));
        when(spins.save(any(RouletteSpin.class))).thenThrow(new DataIntegrityViolationException("duplicado"));

        RouletteService.Spin result = service.spin("user-1");

        assertThat(result.alreadySpun()).isTrue();
        assertThat(result.category().id).isEqualTo("gatos");
    }

    @Test
    void discountsTenPercentOfTheWinningCategoryItemsOnly() {
        service.spin("user-1"); // juguetes

        RouletteService.Applied applied = service.discountFor("user-1", List.of(item("p2", 3, 500), item("p14", 1, 1300)));

        assertThat(applied.categoryId()).isEqualTo("juguetes");
        assertThat(applied.amount()).isEqualTo(150.0); // 10 % de 3 x 500; el shampoo no cuenta
    }

    @Test
    void noDiscountWithoutASpinOrWithoutProductsOfTheCategory() {
        assertThat(service.discountFor("user-1", List.of(item("p2", 1, 500)))).isNull();

        service.spin("user-1"); // juguetes
        assertThat(service.discountFor("user-1", List.of(item("p14", 2, 1300)))).isNull();
        assertThat(service.discountFor("user-1", List.of())).isNull();
        assertThat(service.discountFor("otra-persona", List.of(item("p2", 1, 500)))).isNull();
    }

    @Test
    void withoutCategoriesTheWheelCannotSpin() {
        CategoryRepository empty = mock(CategoryRepository.class);
        when(empty.findAll()).thenReturn(List.of());
        RouletteService noCategories = new RouletteService(spins, empty, mock(ProductRepository.class), Clock.systemUTC(), new Random(), ZONE);

        assertThatThrownBy(() -> noCategories.spin("user-1")).isInstanceOf(IllegalStateException.class);
        verify(spins, never()).save(any());
    }
}
