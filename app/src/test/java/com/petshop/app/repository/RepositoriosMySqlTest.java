package com.petshop.app.repository;

import com.petshop.app.model.OutboxEvent;
import com.petshop.app.model.Order;
import com.petshop.app.model.ProcessedEvent;
import com.petshop.app.model.Product;
import com.petshop.app.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Repositorios contra un MySQL real (el mismo motor que usa la aplicación) en un contenedor, sin bases en memoria: así se
 * prueban de verdad las restricciones de unicidad, las columnas TEXT, el orden de las consultas y la comparación de
 * textos sin distinguir mayúsculas. Se omiten solas si Docker no está disponible (en el CI sí lo está).
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class RepositoriosMySqlTest {

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4");

    @Autowired
    private OrderRepository orders;
    @Autowired
    private UserRepository users;
    @Autowired
    private ProductRepository products;
    @Autowired
    private OutboxEventRepository outbox;
    @Autowired
    private ProcessedEventRepository processed;

    private static Order order(String userId, Instant fecha, String estado, double total) {
        Order o = new Order(userId, fecha, new ArrayList<>(List.of(new Order.OrderItem("p1", 2, total / 2))), total, estado);
        o.shippingCity = "CABA";
        return o;
    }

    // ---- Ventas -------------------------------------------------------------------------------------------------------

    @Test
    void anOrderKeepsItsItemsWhenItIsSavedAndReadBack() {
        Order saved = orders.saveAndFlush(order("u-items", Instant.now(), "COMPLETADA", 2000.0));

        Order read = orders.findById(saved.id).orElseThrow();

        assertThat(read.id).isNotNull();
        assertThat(read.items).hasSize(1);
        assertThat(read.items.get(0).productId).isEqualTo("p1");
        assertThat(read.items.get(0).quantity).isEqualTo(2);
        assertThat(read.total).isEqualTo(2000.0);
        assertThat(read.intentosPago).isZero();
    }

    @Test
    void aCustomersOrdersComeNewestFirstAndOnlyTheirOwn() {
        Instant now = Instant.now();
        orders.save(order("u-orden", now.minusSeconds(300), "COMPLETADA", 100));
        Order newest = orders.save(order("u-orden", now, "ENVIADA", 300));
        orders.save(order("u-orden", now.minusSeconds(100), "CANCELADA", 200));
        orders.save(order("otro-usuario", now, "COMPLETADA", 999));
        orders.flush();

        List<Order> mine = orders.findByUserIdOrderByFechaDesc("u-orden");

        assertThat(mine).hasSize(3);
        assertThat(mine.get(0).id).isEqualTo(newest.id);
        assertThat(mine).extracting(o -> o.total).containsExactly(300.0, 200.0, 100.0);
        assertThat(mine).allSatisfy(o -> assertThat(o.userId).isEqualTo("u-orden"));
    }

    @Test
    void pendingOrdersAreFoundByState() {
        orders.save(order("u-pend", Instant.now(), "PAGO_PENDIENTE", 100));
        orders.save(order("u-pend", Instant.now(), "COMPLETADA", 100));
        orders.flush();

        assertThat(orders.findByEstado("PAGO_PENDIENTE")).extracting(o -> o.userId).contains("u-pend");
        assertThat(orders.findByEstado("PAGO_PENDIENTE")).allSatisfy(o -> assertThat(o.estado).isEqualTo("PAGO_PENDIENTE"));
    }

    // ---- Usuarios -----------------------------------------------------------------------------------------------------

    @Test
    void twoUsersCannotShareAnEmail() {
        users.saveAndFlush(new User("u-a", "duplicado@ejemplo.com", "hash", "A"));

        assertThatThrownBy(() -> users.saveAndFlush(new User("u-b", "duplicado@ejemplo.com", "hash", "B")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void emailsAreComparedWithoutCaseSoNobodyCanRegisterTheSameAddressInCapitals() {
        users.saveAndFlush(new User("u-case", "persona@ejemplo.com", "hash", "Persona"));

        assertThat(users.findByEmail("PERSONA@EJEMPLO.COM")).isPresent();
        assertThatThrownBy(() -> users.saveAndFlush(new User("u-case2", "Persona@Ejemplo.com", "hash", "Otra")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aNewUserIsActiveAndACustomerByDefault() {
        users.saveAndFlush(new User("u-def", "defecto@ejemplo.com", "hash", "Defecto"));

        User read = users.findById("u-def").orElseThrow();

        assertThat(read.active).isTrue();
        assertThat(read.role).isEqualTo("CUSTOMER");
    }

    // ---- Catálogo -----------------------------------------------------------------------------------------------------

    @Test
    void productsAreFoundByCategoryIgnoringCase() {
        products.saveAndFlush(new Product("t-1", "Pelota", "Marca", 100.0, null, 4.0, "/img.svg", null, "juguetes-t", 5));
        products.saveAndFlush(new Product("t-2", "Collar", "Marca", 100.0, null, 4.0, "/img.svg", null, "accesorios-t", 5));

        assertThat(products.findByCategoryIdIgnoreCase("JUGUETES-T")).extracting(p -> p.id).containsExactly("t-1");
    }

    // ---- Mensajería (outbox y consumidor idempotente) -----------------------------------------------------------------

    @Test
    void anOutboxEventCannotBeStoredTwiceWithTheSameEventId() {
        outbox.saveAndFlush(new OutboxEvent("evt-unico", "ventas.confirmadas", "{}", Instant.now()));

        assertThatThrownBy(() -> outbox.saveAndFlush(new OutboxEvent("evt-unico", "ventas.confirmadas", "{}", Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theOutboxStoresLongPayloadsWithoutTruncatingThem() {
        String big = "{\"x\":\"" + "a".repeat(20_000) + "\"}";
        OutboxEvent saved = outbox.saveAndFlush(new OutboxEvent("evt-largo", "ventas.confirmadas", big, Instant.now()));

        assertThat(outbox.findById(saved.id).orElseThrow().payload).hasSize(big.length()).isEqualTo(big);
    }

    @Test
    void pendingOutboxEventsComeOldestFirstSentOnesAreSkippedAndTheBatchIsLimited() {
        for (int i = 0; i < 55; i++) {
            outbox.save(new OutboxEvent("lote-" + i, "ventas.confirmadas", "{\"i\":" + i + "}", Instant.now()));
        }
        OutboxEvent sent = outbox.save(new OutboxEvent("ya-enviado", "ventas.confirmadas", "{}", Instant.now()));
        sent.sentAt = Instant.now();
        outbox.saveAndFlush(sent);

        List<OutboxEvent> batch = outbox.findTop50BySentAtIsNullOrderByIdAsc();

        assertThat(batch).hasSize(50);
        assertThat(batch).extracting(e -> e.eventId).doesNotContain("ya-enviado");
        assertThat(batch).extracting(e -> e.id).isSorted();
        assertThat(outbox.countBySentAtIsNull()).isGreaterThanOrEqualTo(55);
    }

    @Test
    void anAlreadyProcessedEventIsRememberedByItsId() {
        processed.saveAndFlush(new ProcessedEvent("evt-procesado", "ventas.confirmadas", Instant.now()));

        assertThat(processed.existsById("evt-procesado")).isTrue();
        assertThat(processed.existsById("evt-nuevo")).isFalse();
    }
}
