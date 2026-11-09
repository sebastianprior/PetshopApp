package com.petshop.app.payment.mercadopago;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Cliente HTTP de la API de MercadoPago. El Access Token viaja solo en el header Authorization. */
@Component
public class HttpMercadoPagoClient implements MercadoPagoClient {

    private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final DateTimeFormatter MP_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxx");

    private final MercadoPagoSettings settings;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public HttpMercadoPagoClient(MercadoPagoSettings settings) {
        this.settings = settings;
    }

    @Override
    public boolean isEnabled() {
        return !settings.accessToken().isEmpty();
    }

    @Override
    public Preference createPreference(PreferenceRequest request) throws MercadoPagoException {
        ObjectNode body = mapper.createObjectNode();
        ObjectNode item = body.putArray("items").addObject();
        item.put("id", "orden-" + request.externalReference());
        item.put("title", request.title());
        item.put("quantity", 1);
        item.put("currency_id", "ARS");
        item.put("unit_price", Math.round(request.amount() * 100) / 100.0);
        body.put("external_reference", request.externalReference());
        ObjectNode back = body.putObject("back_urls");
        back.put("success", request.backUrl());
        back.put("pending", request.backUrl());
        back.put("failure", request.backUrl());
        if (request.autoReturn()) {
            body.put("auto_return", "approved");
        }
        if (request.notificationUrl() != null && !request.notificationUrl().isBlank()) {
            body.put("notification_url", request.notificationUrl());
        }
        // Solo "aprobado" o "rechazado": sin estados intermedios que complicarían el pedido
        body.put("binary_mode", true);
        body.put("statement_descriptor", "PETSHOP");
        body.put("expires", true);
        body.put("expiration_date_from", MP_DATE.format(OffsetDateTime.now(ARGENTINA)));
        body.put("expiration_date_to", MP_DATE.format(OffsetDateTime.ofInstant(request.expiresAt(), ARGENTINA)));

        JsonNode response = send("POST", "/checkout/preferences", body.toString(), null);
        String initPoint = text(response, "init_point");
        if (initPoint.isEmpty()) {
            initPoint = text(response, "sandbox_init_point");
        }
        if (initPoint.isEmpty() || text(response, "id").isEmpty()) {
            throw new MercadoPagoException("MercadoPago no devolvió el link de pago");
        }
        return new Preference(text(response, "id"), initPoint);
    }

    @Override
    public List<Payment> paymentsOf(String externalReference) throws MercadoPagoException {
        String query = "external_reference=" + URLEncoder.encode(externalReference, StandardCharsets.UTF_8)
                + "&sort=date_created&criteria=desc&limit=20";
        JsonNode response = send("GET", "/v1/payments/search?" + query, null, null);
        List<Payment> payments = new ArrayList<>();
        for (JsonNode node : response.path("results")) {
            payments.add(toPayment(node));
        }
        return payments;
    }

    @Override
    public Optional<Payment> payment(String paymentId) throws MercadoPagoException {
        if (paymentId == null || !paymentId.matches("\\d{1,20}")) {
            return Optional.empty();
        }
        return Optional.of(toPayment(send("GET", "/v1/payments/" + paymentId, null, null)));
    }

    @Override
    public void refund(String paymentId) throws MercadoPagoException {
        if (paymentId == null || !paymentId.matches("\\d{1,20}")) {
            throw new MercadoPagoException("Identificador de pago inválido");
        }
        send("POST", "/v1/payments/" + paymentId + "/refunds", "{}", UUID.randomUUID().toString());
    }

    private Payment toPayment(JsonNode node) {
        return new Payment(text(node, "id"), text(node, "status"), node.path("transaction_amount").asDouble(0), text(node, "external_reference"));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }

    private JsonNode send(String method, String path, String body, String idempotencyKey) throws MercadoPagoException {
        if (!isEnabled()) {
            throw new MercadoPagoException("MercadoPago no está configurado");
        }
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(settings.apiUrl() + path))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + settings.accessToken())
                .header("Accept", "application/json");
        if (idempotencyKey != null) {
            request.header("X-Idempotency-Key", idempotencyKey);
        }
        if (body != null) {
            request.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        }
        try {
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                String detail = response.body() == null ? "" : response.body();
                throw new MercadoPagoException("MercadoPago respondió " + response.statusCode() + ": "
                        + detail.substring(0, Math.min(detail.length(), 300)));
            }
            return mapper.readTree(response.body() == null || response.body().isBlank() ? "{}" : response.body());
        } catch (IOException e) {
            throw new MercadoPagoException("No se pudo comunicar con MercadoPago", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MercadoPagoException("Se interrumpió la comunicación con MercadoPago", e);
        }
    }

    /** Solo para tests y registros: fecha en el formato de MercadoPago. */
    static String formatDate(Instant instant) {
        return MP_DATE.format(OffsetDateTime.ofInstant(instant, ARGENTINA));
    }
}
