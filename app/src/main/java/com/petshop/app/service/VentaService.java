package com.petshop.app.service;

import com.petshop.app.dto.VentaDTO;
import com.petshop.app.dto.VentaRequest;
import com.petshop.app.model.CartItem;
import com.petshop.app.model.Coupon;
import com.petshop.app.model.IdempotencyRecord;
import com.petshop.app.model.Order;
import com.petshop.app.model.Product;
import com.petshop.app.model.ProductVariant;
import com.petshop.app.payment.AuthResult;
import com.petshop.app.payment.GatewayFaultException;
import com.petshop.app.payment.PaymentGateway;
import com.petshop.app.payment.PaymentStatus;
import com.petshop.app.repository.CartItemRepository;
import com.petshop.app.repository.CouponRepository;
import com.petshop.app.repository.IdempotencyRecordRepository;
import com.petshop.app.repository.OrderRepository;
import com.petshop.app.repository.ProductRepository;
import com.petshop.app.repository.ProductVariantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Orquesta el checkout de la API REST v1: reservar stock, autorizar el pago en la pasarela (con timeout),
 * confirmar la venta y publicar el evento. Si la pasarela no responde, la venta queda en PAGO_PENDIENTE
 * y el worker consulta/reintenta usando siempre el mismo idTransaccion.
 */
@Service
public class VentaService {

    public static final String PAGO_PENDIENTE = "PAGO_PENDIENTE";
    public static final String COMPLETADA = "COMPLETADA";
    public static final String ENVIADA = "ENVIADA";
    public static final String CANCELADA = "CANCELADA";

    private static final Logger LOG = LoggerFactory.getLogger(VentaService.class);
    private static final double SHIPPING_COST = 1500.0;

    public record VentaOutcome(int status, Order order, String problemSlug, String problemTitle, String problemDetail) {
        public boolean isProblem() {
            return problemSlug != null;
        }
    }

    private enum AuthKind { APROBADO, RECHAZADO, DATOS_INVALIDOS, SIN_RESPUESTA }

    private record AuthOutcome(AuthKind kind, String motivo) {}

    private final OrderRepository orderRepository;
    private final CartItemRepository cartItemRepository;
    private final InMemoryStore store;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final CouponRepository couponRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final PaymentGateway gateway;
    private final ExecutorService paymentExecutor;
    private final ApplicationEventPublisher publisher;
    private final long timeoutMs;
    private final int maxAttempts;
    private final long minPendingAgeMs;

    public VentaService(OrderRepository orderRepository, CartItemRepository cartItemRepository, InMemoryStore store,
                        ProductRepository productRepository, ProductVariantRepository variantRepository,
                        CouponRepository couponRepository, IdempotencyRecordRepository idempotencyRepository,
                        PaymentGateway gateway, @Qualifier("paymentExecutor") ExecutorService paymentExecutor,
                        ApplicationEventPublisher publisher,
                        @Value("${petshop.payments.timeout-ms:5000}") long timeoutMs,
                        @Value("${petshop.payments.max-attempts:3}") int maxAttempts,
                        @Value("${petshop.payments.min-pending-age-ms:10000}") long minPendingAgeMs) {
        this.orderRepository = orderRepository;
        this.cartItemRepository = cartItemRepository;
        this.store = store;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.couponRepository = couponRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.gateway = gateway;
        this.paymentExecutor = paymentExecutor;
        this.publisher = publisher;
        this.timeoutMs = timeoutMs;
        this.maxAttempts = maxAttempts;
        this.minPendingAgeMs = minPendingAgeMs;
    }

    public VentaDTO toDto(Order order) {
        return VentaDTO.from(order, productId ->
                productRepository.findById(productId).map(p -> p.name).orElse(productId));
    }

    public VentaOutcome crear(String userId, String idemKey, VentaRequest req) {
        String hash = req.hash();
        IdempotencyRecord previous = idempotencyRepository.findById(idemKey).orElse(null);
        if (previous != null) {
            return replay(previous, userId, hash);
        }

        if (blank(req.medioPago)) {
            return problem(422, "medio-pago-requerido", "Medio de pago requerido", "Indicá el token del medio de pago.");
        }
        if (blank(req.nombre) || blank(req.direccion) || blank(req.ciudad)) {
            return problem(422, "datos-envio-incompletos", "Datos de envío incompletos",
                    "Completá nombre, dirección y ciudad.");
        }

        List<CartItem> cart = cartOf(userId);
        if (cart.isEmpty()) {
            return problem(422, "carrito-vacio", "Carrito vacío", "No hay productos en el carrito.");
        }
        for (CartItem item : cart) {
            Product product = productRepository.findById(item.productId).orElse(null);
            if (product == null || availableStock(product, item.variantId) < item.quantity) {
                String name = product != null ? product.name : item.productId;
                return problem(409, "stock-insuficiente", "Stock insuficiente", "Stock insuficiente para " + name);
            }
        }

        double subtotal = cart.stream().mapToDouble(i -> i.price * i.quantity).sum();
        Coupon coupon = null;
        double discount = 0;
        if (!blank(req.cupon)) {
            coupon = couponRepository.findByCodeIgnoreCase(req.cupon.trim()).orElse(null);
            if (coupon == null || !coupon.active || coupon.isExpired() || !coupon.hasUsesLeft()
                    || subtotal < coupon.minPurchase) {
                return problem(422, "cupon-invalido", "Cupón inválido", "El cupón no es válido para esta compra.");
            }
            discount = coupon.computeDiscount(subtotal);
        }

        reservarStock(cart);

        Order order = new Order(userId, Instant.now(),
                cart.stream().map(i -> new Order.OrderItem(i.productId, i.quantity, i.price, i.variant, i.variantId)).toList(),
                subtotal + SHIPPING_COST - discount, PAGO_PENDIENTE);
        order.subtotal = subtotal;
        order.shippingCost = SHIPPING_COST;
        order.discountAmount = discount;
        order.couponCode = coupon != null ? coupon.code : null;
        order.shippingName = req.nombre.trim();
        order.shippingAddress = req.direccion.trim();
        order.shippingCity = req.ciudad.trim();
        order.shippingPostalCode = req.codigoPostal != null ? req.codigoPostal.trim() : "";
        order.shippingPhone = req.telefono != null ? req.telefono.trim() : "";
        order.idTransaccion = java.util.UUID.randomUUID().toString();
        order.estadoPago = "PENDIENTE";
        order.medioPagoToken = req.medioPago.trim();
        order.intentosPago = 1;
        orderRepository.save(order);

        AuthOutcome auth = autorizar(order);
        switch (auth.kind()) {
            case APROBADO -> {
                consumirCarritoYCupon(userId, cart, coupon);
                confirmar(order);
                remember(idemKey, userId, hash, 201, order.id, null, null, null);
                return new VentaOutcome(201, order, null, null, null);
            }
            case SIN_RESPUESTA -> {
                consumirCarritoYCupon(userId, cart, coupon);
                orderRepository.save(order);
                remember(idemKey, userId, hash, 202, order.id, null, null, null);
                return new VentaOutcome(202, order, null, null, null);
            }
            case RECHAZADO -> {
                liberarStock(order);
                orderRepository.delete(order);
                String detail = "El pago fue rechazado: " + auth.motivo();
                remember(idemKey, userId, hash, 402, null, "pago-rechazado", "Pago rechazado", detail);
                return problem(402, "pago-rechazado", "Pago rechazado", detail);
            }
            default -> {
                liberarStock(order);
                orderRepository.delete(order);
                String detail = "La pasarela rechazó los datos de pago (PG-400).";
                remember(idemKey, userId, hash, 422, null, "pago-datos-invalidos", "Datos de pago inválidos", detail);
                return problem(422, "pago-datos-invalidos", "Datos de pago inválidos", detail);
            }
        }
    }

    public VentaOutcome cancelarPorSolicitud(Order order) {
        if (!PAGO_PENDIENTE.equals(order.estado) && !COMPLETADA.equals(order.estado)) {
            return problem(409, "transicion-invalida", "No se puede cancelar",
                    "Una venta en estado " + order.estado + " no se puede cancelar.");
        }
        cancelar(order, "Cancelada por solicitud");
        return new VentaOutcome(200, order, null, null, null);
    }

    public VentaOutcome cambiarEstado(Order order, String nuevoEstado) {
        if (ENVIADA.equals(nuevoEstado)) {
            if (!COMPLETADA.equals(order.estado)) {
                return problem(409, "transicion-invalida", "Transición inválida",
                        "Solo una venta COMPLETADA puede pasar a ENVIADA.");
            }
            order.estado = ENVIADA;
            orderRepository.save(order);
            return new VentaOutcome(200, order, null, null, null);
        }
        if (CANCELADA.equals(nuevoEstado)) {
            return cancelarPorSolicitud(order);
        }
        return problem(422, "estado-invalido", "Estado inválido", "El estado debe ser ENVIADA o CANCELADA.");
    }

    public void procesarPendientes() {
        for (Order order : orderRepository.findByEstado(PAGO_PENDIENTE)) {
            if (order.fecha != null && order.fecha.isAfter(Instant.now().minusMillis(minPendingAgeMs))) {
                continue;
            }
            try {
                procesarPendiente(order);
            } catch (RuntimeException e) {
                LOG.error("Error procesando el pago pendiente de la venta {}", order.id, e);
            }
        }
    }

    void procesarPendiente(Order order) {
        Optional<AuthResult> existente = consultar(order.idTransaccion);
        if (existente.isPresent()) {
            if (existente.get().estado() == PaymentStatus.APROBADO) {
                confirmar(order);
            } else {
                cancelar(order, "Pago rechazado: " + existente.get().motivo());
            }
            return;
        }

        if (order.intentosPago >= maxAttempts) {
            fallarDefinitivamente(order);
            return;
        }

        order.intentosPago++;
        AuthOutcome auth = autorizar(order);
        switch (auth.kind()) {
            case APROBADO -> confirmar(order);
            case RECHAZADO -> cancelar(order, "Pago rechazado: " + auth.motivo());
            case DATOS_INVALIDOS -> cancelar(order, "Datos de pago inválidos");
            default -> {
                orderRepository.save(order);
                if (order.intentosPago >= maxAttempts) {
                    fallarDefinitivamente(order);
                }
            }
        }
    }

    private void fallarDefinitivamente(Order order) {
        LOG.warn("[DLQ pagos.fallidos] venta={} idTransaccion={} intentos={}",
                order.id, order.idTransaccion, order.intentosPago);
        cancelar(order, "No se pudo procesar el pago tras " + maxAttempts + " intentos");
    }

    private void confirmar(Order order) {
        order.estado = COMPLETADA;
        order.estadoPago = "APROBADO";
        orderRepository.save(order);
        publisher.publishEvent(new VentaEvents.VentaConfirmada(order.id, order.userId, order.total));
    }

    private void cancelar(Order order, String motivo) {
        boolean estabaPagada = "APROBADO".equals(order.estadoPago);
        liberarStock(order);
        if (order.couponCode != null) {
            couponRepository.findByCodeIgnoreCase(order.couponCode).ifPresent(c -> {
                c.usesCount = Math.max(0, c.usesCount - 1);
                couponRepository.save(c);
            });
        }
        order.estado = CANCELADA;
        order.estadoPago = estabaPagada ? "REEMBOLSADO" : "CANCELADO";
        orderRepository.save(order);
        publisher.publishEvent(new VentaEvents.VentaCancelada(order.id, order.userId, motivo));
    }

    private AuthOutcome autorizar(Order order) {
        Future<AuthResult> future = paymentExecutor.submit(
                () -> gateway.autorizar(order.idTransaccion, order.total, order.medioPagoToken));
        try {
            AuthResult result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            return result.estado() == PaymentStatus.APROBADO
                    ? new AuthOutcome(AuthKind.APROBADO, null)
                    : new AuthOutcome(AuthKind.RECHAZADO, result.motivo());
        } catch (TimeoutException e) {
            LOG.warn("La pasarela no respondió en {} ms (idTransaccion={})", timeoutMs, order.idTransaccion);
            return new AuthOutcome(AuthKind.SIN_RESPUESTA, null);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof GatewayFaultException fault
                    && GatewayFaultException.INVALID_DATA.equals(fault.getCode())) {
                return new AuthOutcome(AuthKind.DATOS_INVALIDOS, fault.getMessage());
            }
            return new AuthOutcome(AuthKind.SIN_RESPUESTA, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new AuthOutcome(AuthKind.SIN_RESPUESTA, null);
        }
    }

    private Optional<AuthResult> consultar(String idTransaccion) {
        Future<Optional<AuthResult>> future = paymentExecutor.submit(() -> gateway.consultarPago(idTransaccion));
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException | ExecutionException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private VentaOutcome replay(IdempotencyRecord previous, String userId, String hash) {
        if (!previous.userId.equals(userId) || !previous.requestHash.equals(hash)) {
            return problem(409, "idempotency-key-reutilizada", "Idempotency-Key ya utilizada",
                    "La clave ya se usó para otra solicitud.");
        }
        if (previous.ordenId != null) {
            Optional<Order> order = orderRepository.findById(previous.ordenId);
            if (order.isPresent()) {
                return new VentaOutcome(previous.statusCode, order.get(), null, null, null);
            }
        }
        return problem(previous.statusCode, previous.problemSlug, previous.problemTitle, previous.problemDetail);
    }

    private void remember(String key, String userId, String hash, int status, Long orderId,
                          String slug, String title, String detail) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.idemKey = key;
        record.userId = userId;
        record.requestHash = hash;
        record.statusCode = status;
        record.ordenId = orderId;
        record.problemSlug = slug;
        record.problemTitle = title;
        record.problemDetail = detail;
        record.createdAt = Instant.now();
        idempotencyRepository.save(record);
    }

    /** Los invitados ("guest:<id>") tienen el carrito en memoria; los usuarios logueados, en la base. */
    private List<CartItem> cartOf(String userId) {
        if (isGuest(userId)) {
            return new ArrayList<>(store.carts.getOrDefault(userId, List.of()));
        }
        return cartItemRepository.findByUserId(userId);
    }

    public static boolean isGuest(String userId) {
        return userId != null && userId.startsWith("guest:");
    }

    private void consumirCarritoYCupon(String userId, List<CartItem> cart, Coupon coupon) {
        if (isGuest(userId)) {
            store.carts.remove(userId);
        } else {
            cartItemRepository.deleteAll(new ArrayList<>(cart));
        }
        if (coupon != null) {
            coupon.usesCount += 1;
            couponRepository.save(coupon);
        }
    }

    private int availableStock(Product product, Long variantId) {
        if (variantId == null) {
            return product.stock;
        }
        return variantRepository.findById(variantId).map(v -> v.stock).orElse(0);
    }

    private void reservarStock(List<CartItem> cart) {
        for (CartItem item : cart) {
            adjustStock(item.productId, item.variantId, -item.quantity);
        }
    }

    private void liberarStock(Order order) {
        for (Order.OrderItem item : order.items) {
            adjustStock(item.productId, item.variantId, item.quantity);
        }
    }

    private void adjustStock(String productId, Long variantId, int delta) {
        productRepository.findById(productId).ifPresent(product -> {
            product.stock += delta;
            productRepository.save(product);
        });
        if (variantId != null) {
            variantRepository.findById(variantId).ifPresent((ProductVariant variant) -> {
                variant.stock += delta;
                variantRepository.save(variant);
            });
        }
    }

    private static VentaOutcome problem(int status, String slug, String title, String detail) {
        return new VentaOutcome(status, null, slug, title, detail);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
