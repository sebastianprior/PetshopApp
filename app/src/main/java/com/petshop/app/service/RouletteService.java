package com.petshop.app.service;

import com.petshop.app.model.CartItem;
import com.petshop.app.model.Category;
import com.petshop.app.model.Product;
import com.petshop.app.model.RouletteSpin;
import com.petshop.app.repository.CategoryRepository;
import com.petshop.app.repository.ProductRepository;
import com.petshop.app.repository.RouletteSpinRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Ruleta del día: cada persona gira una vez por día y gana un 10 % de descuento en una categoría sorteada,
 * válido hasta la medianoche. El sorteo lo hace el servidor (el cliente solo anima el resultado) y el descuento
 * se aplica de verdad en el checkout.
 */
@Service
public class RouletteService {

    public static final int PERCENT = 10;

    /** Resultado de girar: la categoría ganada y si ya se había girado hoy. */
    public record Spin(Category category, LocalDate day, boolean alreadySpun) {}

    /** Descuento que corresponde a un carrito. */
    public record Applied(String categoryId, String categoryName, double amount) {}

    private final RouletteSpinRepository spins;
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final Clock clock;
    private final Random random;
    private final ZoneId zone;

    @Autowired
    public RouletteService(RouletteSpinRepository spins, CategoryRepository categories, ProductRepository products,
                           @Value("${petshop.roulette.zone:America/Argentina/Buenos_Aires}") String zone) {
        this(spins, categories, products, Clock.system(ZoneId.of(zone)), new SecureRandom(), ZoneId.of(zone));
    }

    RouletteService(RouletteSpinRepository spins, CategoryRepository categories, ProductRepository products,
                    Clock clock, Random random, ZoneId zone) {
        this.spins = spins;
        this.categories = categories;
        this.products = products;
        this.clock = clock;
        this.random = random;
        this.zone = zone;
    }

    /**
     * Los gajos de la ruleta: las categorías que tienen al menos un producto con stock (un premio en una categoría
     * vacía no serviría), en un orden fijo.
     */
    public List<Category> wheel() {
        return categories.findAll().stream()
                .filter(category -> products.findByCategoryIdIgnoreCase(category.id).stream().anyMatch(product -> product.stock > 0))
                .sorted(Comparator.comparing(c -> c.id))
                .toList();
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /** Hasta cuándo vale el descuento de hoy (medianoche en la zona horaria de la tienda). */
    public Instant validUntil() {
        return today().plusDays(1).atStartOfDay(zone).toInstant();
    }

    public Optional<Category> todaysCategory(String owner) {
        return spins.findByOwnerKeyAndDay(owner, today()).flatMap(spin -> categories.findById(spin.categoryId));
    }

    /** Gira la ruleta: la primera vez del día sortea una categoría; las siguientes devuelven la misma. */
    public Spin spin(String owner) {
        LocalDate day = today();
        Optional<RouletteSpin> existing = spins.findByOwnerKeyAndDay(owner, day);
        if (existing.isPresent()) {
            return resultOf(existing.get(), true);
        }

        List<Category> wheel = wheel();
        if (wheel.isEmpty()) {
            throw new IllegalStateException("No hay categorías para sortear");
        }
        Category won = wheel.get(random.nextInt(wheel.size()));
        try {
            spins.save(new RouletteSpin(owner, day, won.id));
            return new Spin(won, day, false);
        } catch (DataIntegrityViolationException raced) {
            // dos giros a la vez de la misma persona: vale el que se guardó primero
            return resultOf(spins.findByOwnerKeyAndDay(owner, day).orElseThrow(), true);
        }
    }

    /** 10 % del valor de los productos del carrito que son de la categoría ganada hoy; null si no hay giro o no aplica. */
    public Applied discountFor(String owner, List<CartItem> items) {
        Optional<Category> category = todaysCategory(owner);
        if (category.isEmpty() || items == null) {
            return null;
        }
        double base = 0;
        for (CartItem item : items) {
            Product product = products.findById(item.productId).orElse(null);
            if (product != null && category.get().id.equalsIgnoreCase(product.categoryId)) {
                base += item.price * item.quantity;
            }
        }
        double amount = Math.round(base * PERCENT) / 100.0;
        return amount > 0 ? new Applied(category.get().id, category.get().name, amount) : null;
    }

    private Spin resultOf(RouletteSpin spin, boolean already) {
        Category category = categories.findById(spin.categoryId).orElseThrow();
        return new Spin(category, spin.day, already);
    }
}
