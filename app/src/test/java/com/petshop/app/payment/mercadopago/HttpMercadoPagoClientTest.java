package com.petshop.app.payment.mercadopago;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petshop.app.payment.mercadopago.MercadoPagoClient.MercadoPagoException;
import com.petshop.app.payment.mercadopago.MercadoPagoClient.Payment;
import com.petshop.app.payment.mercadopago.MercadoPagoClient.Preference;
import com.petshop.app.payment.mercadopago.MercadoPagoClient.PreferenceRequest;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Prueba el cliente contra un servidor HTTP local que hace de MercadoPago. */
class HttpMercadoPagoClientTest {

    private static final String TOKEN = "TEST-token-secreto-123";
    private static final ObjectMapper JSON = new ObjectMapper();

    private HttpServer server;
    private final AtomicReference<String> method = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> idempotency = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicInteger requests = new AtomicInteger();
    private volatile int status = 200;
    private volatile String responseBody = "{}";
    private HttpMercadoPagoClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().toString());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            idempotency.set(exchange.getRequestHeaders().getFirst("X-Idempotency-Key"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        client = clientWithToken(TOKEN);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private HttpMercadoPagoClient clientWithToken(String token) {
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        return new HttpMercadoPagoClient(new MercadoPagoSettings(token, url, 30, "http://localhost:5173", "", true));
    }

    private PreferenceRequest request(boolean autoReturn, String notificationUrl) {
        return new PreferenceRequest("12", "Compra en Petshop #12", 14199.005, "https://tienda.example/pago/resultado?orden=12",
                notificationUrl, autoReturn, Instant.now().plusSeconds(1800));
    }

    @Test
    void createsAPreferenceWithTheOrderAmountReferenceAndReturnUrls() throws Exception {
        responseBody = "{\"id\":\"pref-123\",\"init_point\":\"https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=pref-123\"}";

        Preference preference = client.createPreference(request(true, "https://api.example/api/payments/mercadopago/webhook"));

        assertThat(preference.id()).isEqualTo("pref-123");
        assertThat(preference.initPoint()).contains("pref_id=pref-123");
        assertThat(method.get()).isEqualTo("POST");
        assertThat(path.get()).isEqualTo("/checkout/preferences");
        assertThat(authorization.get()).isEqualTo("Bearer " + TOKEN);

        JsonNode sent = JSON.readTree(body.get());
        assertThat(sent.path("external_reference").asText()).isEqualTo("12");
        assertThat(sent.path("items")).hasSize(1);
        assertThat(sent.path("items").get(0).path("quantity").asInt()).isEqualTo(1);
        assertThat(sent.path("items").get(0).path("currency_id").asText()).isEqualTo("ARS");
        assertThat(sent.path("items").get(0).path("unit_price").asDouble()).isEqualTo(14199.01); // dos decimales
        assertThat(sent.path("back_urls").path("success").asText()).isEqualTo("https://tienda.example/pago/resultado?orden=12");
        assertThat(sent.path("back_urls").path("failure").asText()).isEqualTo("https://tienda.example/pago/resultado?orden=12");
        assertThat(sent.path("back_urls").path("pending").asText()).isEqualTo("https://tienda.example/pago/resultado?orden=12");
        assertThat(sent.path("auto_return").asText()).isEqualTo("approved");
        assertThat(sent.path("notification_url").asText()).isEqualTo("https://api.example/api/payments/mercadopago/webhook");
        assertThat(sent.path("binary_mode").asBoolean()).isTrue();
        assertThat(sent.path("expires").asBoolean()).isTrue();
        assertThat(sent.path("expiration_date_to").asText()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}-03:00");
    }

    @Test
    void leavesOutAutoReturnAndNotificationUrlWhenTheyDoNotApply() throws Exception {
        responseBody = "{\"id\":\"p\",\"init_point\":\"https://mp/x\"}";

        client.createPreference(request(false, null));

        JsonNode sent = JSON.readTree(body.get());
        assertThat(sent.has("auto_return")).isFalse();
        assertThat(sent.has("notification_url")).isFalse();
    }

    @Test
    void usesTheSandboxLinkWhenThereIsNoRegularOne() throws Exception {
        responseBody = "{\"id\":\"p\",\"sandbox_init_point\":\"https://sandbox.mp/x\"}";

        assertThat(client.createPreference(request(false, null)).initPoint()).isEqualTo("https://sandbox.mp/x");
    }

    @Test
    void failsWhenMercadoPagoDoesNotReturnALink() {
        responseBody = "{\"id\":\"p\"}";

        assertThatThrownBy(() -> client.createPreference(request(false, null))).isInstanceOf(MercadoPagoException.class);
    }

    @Test
    void anErrorFromMercadoPagoBecomesAnExceptionThatNeverLeaksTheToken() {
        status = 401;
        responseBody = "{\"message\":\"invalid_token\",\"status\":401}";

        assertThatThrownBy(() -> client.createPreference(request(false, null)))
                .isInstanceOf(MercadoPagoException.class)
                .hasMessageContaining("401")
                .hasMessageNotContaining(TOKEN);
    }

    @Test
    void readsThePaymentsOfAnOrderByItsReference() throws Exception {
        responseBody = "{\"results\":[{\"id\":987654321,\"status\":\"approved\",\"transaction_amount\":14199.0,\"external_reference\":\"12\"},"
                + "{\"id\":987654320,\"status\":\"rejected\",\"transaction_amount\":14199.0,\"external_reference\":\"12\"}]}";

        List<Payment> payments = client.paymentsOf("12");

        assertThat(method.get()).isEqualTo("GET");
        assertThat(path.get()).startsWith("/v1/payments/search?external_reference=12");
        assertThat(payments).extracting(Payment::status).containsExactly("approved", "rejected");
        assertThat(payments.get(0).id()).isEqualTo("987654321");
        assertThat(payments.get(0).amount()).isEqualTo(14199.0);
        assertThat(payments.get(0).externalReference()).isEqualTo("12");
    }

    @Test
    void readsASinglePaymentAndRejectsIdsThatAreNotNumbersWithoutCallingOut() throws Exception {
        responseBody = "{\"id\":55,\"status\":\"pending\",\"transaction_amount\":10.5,\"external_reference\":\"7\"}";

        Optional<Payment> payment = client.payment("55");
        assertThat(payment).isPresent();
        assertThat(payment.get().status()).isEqualTo("pending");
        assertThat(path.get()).isEqualTo("/v1/payments/55");

        int before = requests.get();
        assertThat(client.payment("../../v1/algo")).isEmpty();
        assertThat(client.payment("")).isEmpty();
        assertThat(client.payment(null)).isEmpty();
        assertThat(requests.get()).isEqualTo(before);
    }

    @Test
    void refundsAPaymentWithAnIdempotencyKey() throws Exception {
        responseBody = "{\"id\":1,\"status\":\"approved\"}";

        client.refund("987654321");

        assertThat(method.get()).isEqualTo("POST");
        assertThat(path.get()).isEqualTo("/v1/payments/987654321/refunds");
        assertThat(idempotency.get()).isNotBlank();
    }

    @Test
    void refusesToRefundSomethingThatIsNotAPaymentId() {
        assertThatThrownBy(() -> client.refund("1/../../x")).isInstanceOf(MercadoPagoException.class);
        assertThat(requests.get()).isZero();
    }

    @Test
    void isDisabledWithoutATokenAndNeverCallsOut() {
        HttpMercadoPagoClient disabled = clientWithToken("  ");

        assertThat(disabled.isEnabled()).isFalse();
        assertThatThrownBy(() -> disabled.createPreference(request(false, null))).isInstanceOf(MercadoPagoException.class);
        assertThat(requests.get()).isZero();
    }

    @Test
    void reportsAnUnreachableMercadoPago() {
        server.stop(0);

        assertThatThrownBy(() -> client.paymentsOf("1")).isInstanceOf(MercadoPagoException.class).hasMessageNotContaining(TOKEN);
    }
}
