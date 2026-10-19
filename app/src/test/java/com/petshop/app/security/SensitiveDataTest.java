package com.petshop.app.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.petshop.app.controller.AuthController;
import com.petshop.app.controller.OrderController;
import com.petshop.app.dto.RegisterRequest;
import com.petshop.app.model.Order;
import com.petshop.app.model.ResetToken;
import com.petshop.app.model.User;
import com.petshop.app.repository.OrderRepository;
import com.petshop.app.repository.ProductRepository;
import com.petshop.app.repository.ResetTokenRepository;
import com.petshop.app.repository.UserRepository;
import com.petshop.app.service.AdminGuard;
import com.petshop.app.service.JwtUtil;
import com.petshop.app.service.LogNotificationService;
import com.petshop.app.service.LoginRateLimiter;
import com.petshop.app.service.RefreshTokenService;
import com.petshop.app.service.TokenHasher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SEC-D01 (los logs no incluyen secretos) y SEC-D02 (las respuestas exponen solo lo necesario).
 * Todos los datos son ficticios.
 */
class SensitiveDataTest {

    private static final String JWT_SECRET = "test-secret-test-secret-test-secret-test-secret";
    private static final String PASSWORD = "Clave-Ficticia-123";
    private static final String NEW_PASSWORD = "Otra-Clave-Ficticia-456";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @TempDir
    Path outbox;

    private final Map<String, User> users = new HashMap<>();
    private final Map<String, ResetToken> resetTokens = new HashMap<>();
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private Logger rootLogger;
    private AuthController auth;
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findByEmail(anyString())).thenAnswer(inv -> Optional.ofNullable(users.get(inv.<String>getArgument(0))));
        when(userRepository.findById(anyString())).thenAnswer(inv ->
                users.values().stream().filter(u -> u.id.equals(inv.getArgument(0))).findFirst());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            users.put(u.email, u);
            return u;
        });
        ResetTokenRepository resetRepository = mock(ResetTokenRepository.class);
        when(resetRepository.save(any(ResetToken.class))).thenAnswer(inv -> {
            ResetToken t = inv.getArgument(0);
            resetTokens.put(t.token, t);
            return t;
        });
        doAnswer(inv -> resetTokens.remove(inv.<ResetToken>getArgument(0).token)).when(resetRepository).delete(any(ResetToken.class));
        when(resetRepository.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(resetTokens.get(inv.<String>getArgument(0))));
        RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
        when(refreshTokens.issue(anyString())).thenAnswer(inv -> TokenHasher.newToken());

        jwtUtil = new JwtUtil(JWT_SECRET, 60_000);
        auth = new AuthController(jwtUtil, userRepository, resetRepository, new LogNotificationService(outbox.toString()),
                new LoginRateLimiter(), refreshTokens);

        rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(Level.DEBUG);
        logs.start();
        rootLogger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        rootLogger.detachAppender(logs);
        logs.stop();
    }

    private String allLogOutput() {
        return logs.list.stream().map(e -> e.getFormattedMessage() + " " + e.getThrowableProxy()).reduce("", (a, b) -> a + "\n" + b);
    }

    private String outboxContent() throws IOException {
        try (Stream<Path> files = Files.list(outbox)) {
            return files.map(p -> {
                try {
                    return Files.readString(p);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }).reduce("", (a, b) -> a + "\n" + b);
        }
    }

    private static String extractCode(String mail) {
        var matcher = java.util.regex.Pattern.compile("es: (\\S+)").matcher(mail);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> body(ResponseEntity<?> response) {
        return (Map<String, Object>) response.getBody();
    }

    @Test
    void authenticationAndPasswordRecoveryNeverWriteSecretsToTheLogs() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.email = "ficticio@example.test";
        request.password = PASSWORD;
        request.name = "Ficticio";
        Map<String, Object> registered = body(auth.register(request));
        Map<String, Object> loggedIn = body(auth.login(Map.of("email", request.email, "password", PASSWORD)));
        auth.login(Map.of("email", request.email, "password", "incorrecta-ficticia"));
        auth.recover(Map.of("email", request.email));
        String code = extractCode(outboxContent());
        auth.reset(Map.of("token", code, "password", NEW_PASSWORD));

        String output = allLogOutput();
        assertThat(output).isNotBlank();
        assertThat(output)
                .doesNotContain(PASSWORD)
                .doesNotContain(NEW_PASSWORD)
                .doesNotContain("incorrecta-ficticia")
                .doesNotContain(code)
                .doesNotContain((String) registered.get("token"))
                .doesNotContain((String) loggedIn.get("token"))
                .doesNotContain((String) loggedIn.get("refreshToken"))
                .doesNotContain(users.get(request.email).password);
        assertThat(output).doesNotContainPattern("eyJ[A-Za-z0-9_-]{10,}\\.");
    }

    @Test
    void theRecoveryCodeReachesTheUserOnlyThroughTheMailboxNeverInTheApiResponse() throws Exception {
        users.put("ficticio@example.test", new User("u-1", "ficticio@example.test", "hash", "Ficticio"));

        ResponseEntity<?> known = auth.recover(Map.of("email", "ficticio@example.test"));
        ResponseEntity<?> unknown = auth.recover(Map.of("email", "no-existe@example.test"));

        String code = extractCode(outboxContent());
        assertThat(known.getBody().toString()).doesNotContain(code);
        assertThat(body(known)).doesNotContainKey("resetToken");
        assertThat(known.getStatusCode().value()).isEqualTo(200);
        assertThat(unknown.getStatusCode()).isEqualTo(known.getStatusCode());
        assertThat(body(unknown)).isEqualTo(body(known));
        try (Stream<Path> files = Files.list(outbox)) {
            assertThat(files.count()).isEqualTo(1);
        }
    }

    @Test
    void theResetCodeIsStoredOnlyAsAHashAndIsSingleUse() throws Exception {
        users.put("ficticio@example.test", new User("u-1", "ficticio@example.test", new BCryptPasswordEncoder().encode(PASSWORD), "Ficticio"));
        auth.recover(Map.of("email", "ficticio@example.test"));
        String code = extractCode(outboxContent());

        assertThat(resetTokens).doesNotContainKey(code).containsKey(TokenHasher.hash(code));
        assertThat(auth.reset(Map.of("token", code, "password", NEW_PASSWORD)).getStatusCode().value()).isEqualTo(200);
        assertThat(resetTokens).isEmpty();
        assertThat(auth.reset(Map.of("token", code, "password", NEW_PASSWORD)).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void weakPasswordsAreRejectedOnResetAndChange() {
        assertThat(auth.reset(Map.of("token", "x", "password", "1")).getStatusCode().value()).isEqualTo(400);
        String token = jwtUtil.generateToken("u-1", "ficticio@example.test", "CUSTOMER");
        assertThat(auth.changePassword(token, Map.of("currentPassword", "a", "newPassword", "1")).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void theUserProfileExposesOnlyTheNecessaryFields() throws Exception {
        User user = new User("u-1", "ficticio@example.test", new BCryptPasswordEncoder().encode(PASSWORD), "Ficticio");
        users.put(user.email, user);
        String token = jwtUtil.generateToken(user.id, user.email, user.role);

        JsonNode me = JSON.readTree(JSON.writeValueAsString(auth.me(token).getBody()));
        Map<String, Object> login = body(auth.login(Map.of("email", user.email, "password", PASSWORD)));
        JsonNode loginUser = JSON.readTree(JSON.writeValueAsString(login.get("user")));

        for (JsonNode node : List.of(me, loginUser)) {
            assertThat(node.propertyNames()).containsExactlyInAnyOrder("id", "email", "name", "role", "active");
        }
        assertThat(JSON.writeValueAsString(me)).doesNotContain(user.password);
    }

    // ---- SEC-D02: pedidos ----

    private Order paidOrder() {
        Order order = new Order("user-1", Instant.now(), List.of(new Order.OrderItem("p1", 1, 1000.0)), 2500.0, "COMPLETADA");
        order.id = 7L;
        order.medioPagoToken = "tok_aprobado";
        order.intentosPago = 2;
        order.idTransaccion = "tx-123";
        order.estadoPago = "APROBADO";
        order.shippingName = "Ficticio";
        order.shippingAddress = "Calle 1";
        return order;
    }

    private OrderController orderController(Order order) {
        OrderRepository repository = mock(OrderRepository.class);
        when(repository.findByUserIdOrderByFechaDesc("user-1")).thenReturn(List.of(order));
        when(repository.findAllByOrderByFechaDesc()).thenReturn(List.of(order));
        return new OrderController(repository, mock(ProductRepository.class), jwtUtil, new AdminGuard(jwtUtil));
    }

    @Test
    void aCustomerSeesOnlyTheNecessaryFieldsOfTheirOrders() throws Exception {
        OrderController controller = orderController(paidOrder());
        String token = jwtUtil.generateToken("user-1", "u@example.test", "CUSTOMER");

        String json = JSON.writeValueAsString(controller.listMine(token).getBody());

        assertThat(json)
                .contains("\"total\"", "\"estado\"", "\"items\"")
                .doesNotContain("medioPagoToken", "tok_aprobado", "intentosPago", "userId", "shippingAddress", "idTransaccion", "password");
    }

    @Test
    void anAdminListingNeverIncludesThePaymentTokenEither() throws Exception {
        OrderController controller = orderController(paidOrder());
        String admin = jwtUtil.generateToken("admin-1", "a@example.test", "ADMIN");

        String json = JSON.writeValueAsString(controller.listAll(admin).getBody());

        assertThat(json).contains("\"userId\"", "\"idTransaccion\"").doesNotContain("medioPagoToken", "tok_aprobado", "intentosPago");
    }

    @Test
    void theOrderEntityNeverSerializesItsPaymentToken() throws Exception {
        String json = JSON.writeValueAsString(paidOrder());

        assertThat(json).doesNotContain("medioPagoToken", "tok_aprobado", "intentosPago");
    }
}
