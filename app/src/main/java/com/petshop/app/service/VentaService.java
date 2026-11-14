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
import com.petshop.app.payment.mercadopago.MercadoPagoClient;
import com.petshop.app.payment.mercadopago.MercadoPagoClient.MercadoPagoException;
import com.petshop.app.payment.mercadopago.MercadoPagoSettings;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
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
    /** Valor de medioPago para pagar con MercadoPago (Checkout Pro). */
    public static final String MERCADOPAGO = "mercadopago";

    private static final Logger LOG = LoggerFactory.getLogger(VentaService.class);

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
    private final RouletteService rouletteService;
    private final ShopRules shopRules;
    private final MercadoPagoClient mercadoPago;
    private final MercadoPagoSettings paymentSettings;
    private final long timeoutMs;
    private final int maxAttempts;
    private final long minPendingAgeMs;

    public VentaService(OrderRepository orderRepository, CartItemRepository cartItemRepository, InMemoryStore store,
                        ProductRepository productRepository, ProductVariantRepository variantRepository,
                        CouponRepository couponRepository, IdempotencyRecordRepository idempotencyRepository,
                        PaymentGateway gateway, @Qualifier("paymentExecutor") ExecutorService paymentExecutor,
                        ApplicationEventPublisher publisher, RouletteService rouletteService, ShopRules shopRules,
                        MercadoPagoClient mercadoPago, MercadoPagoSettings paymentSettings,
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
        this.rouletteService = rouletteService;
        this.shopRules = shopRules;
        this.mercadoPago = mercadoPago;
        this.paymentSettings = paymentSettings;
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
        boolean usaMercadoPago = MERCADOPAGO.equalsIgnoreCase(req.medioPago.trim());
        if (usaMercadoPago ? !mercadoPago.isEnabled() : !paymentSettings.simulatedEnabled()) {
            return problem(422, "medio-pago-no-disponible", "Medio de pago no disponible",
                    usaMercadoPago ? "MercadoPago no está disponible en este momento." : "Ese medio de pago no está disponible.");
        }
        if (blank(req.nombre) || blank(req.direccion) || blank(req.ciudad)) {
            return problem(422, "datos-envio-incompletos", "Datos de envío incompletos",
                    "Completá nombre, dirección y ciudad.");
        }

        Integer postalCode = ShopRules.parsePostalCode(req.codigoPostal);
        if (postalCode == null) {
            return problem(422, "codigo-postal-invalido", "Código postal inválido",
                    "Ingresá un código postal válido de 4 números para calcular el envío.");
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

        // Ruleta del día: 10 % en la categoría sorteada. Nunca puede dejar el subtotal por debajo de cero junto con el cupón.
        RouletteService.Applied roulette = rouletteService.discountFor(userId, cart);
        double rouletteDiscount = roulette != null ? Math.min(roulette.amount(), Math.max(0, subtotal - discount)) : 0;

        // Reglas del administrador: envío gratis a partir de un monto y producto de regalo (se suma al pedido sin costo)
        double shippingCost = shopRules.shippingCost(subtotal, postalCode);
        Product gift = shopRules.giftFor(subtotal, cart).orElse(null);

        // La reserva atómica es la que decide: la comprobación de arriba solo sirve para responder rápido, porque entre la
        // comprobación y el descuento otra persona puede haberse llevado la última unidad.
        String sinStock = reservarStock(cart);
        if (sinStock != null) {
            return problem(409, "stock-insuficiente", "Stock insuficiente", "Stock insuficiente para " + sinStock);
        }
        if (gift != null && !reservarUno(gift.id, null, 1)) {
            gift = null; // el regalo se agotó mientras tanto: se vende igual, sin regalo
        }

        List<Order.OrderItem> orderItems = new ArrayList<>(
                cart.stream().map(i -> new Order.OrderItem(i.productId, i.quantity, i.price, i.variant, i.variantId)).toList());
        if (gift != null) {
            orderItems.add(new Order.OrderItem(gift.id, 1, 0.0, ShopRules.GIFT_LABEL, null));
        }

        Order order = new Order(userId, Instant.now(), orderItems,
                subtotal + shippingCost - discount - rouletteDiscount, PAGO_PENDIENTE);
        order.subtotal = subtotal;
        order.shippingCost = shippingCost;
        order.discountAmount = discount + rouletteDiscount;
        order.ruletaDescuento = rouletteDiscount;
        order.ruletaCategoria = rouletteDiscount > 0 ? roulette.categoryId() : null;
        order.couponCode = coupon != null ? coupon.code : null;
        order.shippingName = req.nombre.trim();
        order.shippingAddress = req.direccion.trim();
        order.shippingCity = req.ciudad.trim();
        order.shippingPostalCode = req.codigoPostal != null ? req.codigoPostal.trim() : "";
        order.shippingPhone = req.telefono != null ? req.telefono.trim() : "";
        order.idTransaccion = java.util.UUID.randomUUID().toString();
        order.estadoPago = "PENDIENTE";
        order.medioPagoToken = usaMercadoPago ? MERCADOPAGO : req.medioPago.trim();
        order.intentosPago = 1;
        orderRepository.save(order);

        if (usaMercadoPago) {
            return iniciarMercadoPago(order, userId, cart, coupon, idemKey, hash);
        }

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
        // Si ya se cobró por MercadoPago, primero se devuelve el dinero: si eso falla, el pedido no se cancela
        if (esMercadoPago(order) && "APROBADO".equals(order.estadoPago) && order.mpPaymentId != null) {
            try {
                mercadoPago.refund(order.mpPaymentId);
            } catch (MercadoPagoException e) {
                LOG.error("No se pudo reembolsar el pago {} de la venta {}: {}", order.mpPaymentId, order.id, e.getMessage());
                return problem(502, "reembolso-fallido", "No se pudo devolver el dinero",
                        "MercadoPago no pudo procesar el reembolso. El pedido sigue activo: probá de nuevo en unos minutos.");
            }
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
        if (esMercadoPago(order)) {
            sincronizarMercadoPago(order);
            return;
        }
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

    // ---------------------------------------------------------------------------------------------------------------
    // MercadoPago (Checkout Pro): el cliente paga en el sitio de MercadoPago y vuelve a la tienda. La confirmación nunca
    // sale de lo que diga el navegador: siempre se le pregunta a MercadoPago por los pagos del pedido.
    // ---------------------------------------------------------------------------------------------------------------

    private static boolean esMercadoPago(Order order) {
        return MERCADOPAGO.equals(order.medioPagoToken);
    }

    /** Crea el link de pago y deja el pedido esperando el pago (202). Si MercadoPago no responde, no queda nada reservado. */
    private VentaOutcome iniciarMercadoPago(Order order, String userId, List<CartItem> cart, Coupon coupon, String idemKey, String hash) {
        order.intentosPago = 0;
        Instant expira = Instant.now().plus(paymentSettings.pendingExpiryMinutes(), ChronoUnit.MINUTES);
        String front = paymentSettings.frontendUrl();
        String notification = paymentSettings.publicApiUrl().isEmpty() ? null : paymentSettings.publicApiUrl() + "/api/payments/mercadopago/webhook";
        try {
            MercadoPagoClient.Preference preference = mercadoPago.createPreference(new MercadoPagoClient.PreferenceRequest(
                    String.valueOf(order.id), "Compra en Petshop #" + order.id, order.total,
                    front + "/pago/resultado?orden=" + order.id, notification, front.startsWith("https://"), expira));
            order.paymentUrl = preference.initPoint();
            order.idTransaccion = preference.id();
        } catch (MercadoPagoException e) {
            LOG.error("No se pudo crear el link de pago de MercadoPago para la venta {}: {}", order.id, e.getMessage());
            liberarStock(order);
            orderRepository.delete(order);
            // No se recuerda en la tabla de idempotencia: el cliente puede reintentar con la misma clave
            return problem(502, "mercadopago-no-disponible", "MercadoPago no respondió",
                    "No pudimos iniciar el pago con MercadoPago. Probá de nuevo en unos minutos.");
        }
        consumirCarritoYCupon(userId, cart, coupon);
        orderRepository.save(order);
        remember(idemKey, userId, hash, 202, order.id, null, null, null);
        return new VentaOutcome(202, order, null, null, null);
    }

    /**
     * Pregunta a MercadoPago por los pagos del pedido: aprobado (con el monto exacto) lo confirma; si venció el plazo sin
     * pago lo cancela y libera el stock; si no, sigue esperando. Lo llaman el worker, el webhook y la vuelta del cliente.
     */
    public Order sincronizarPago(Order order) {
        if (esMercadoPago(order)) {
            sincronizarMercadoPago(order);
        }
        return orderRepository.findById(order.id).orElse(order);
    }

    private void sincronizarMercadoPago(Order order) {
        List<MercadoPagoClient.Payment> payments;
        try {
            payments = mercadoPago.paymentsOf(String.valueOf(order.id));
        } catch (MercadoPagoException e) {
            LOG.warn("No se pudo consultar a MercadoPago por la venta {}: {}", order.id, e.getMessage());
            return;
        }
        Optional<MercadoPagoClient.Payment> approved = payments.stream()
                .filter(p -> "approved".equals(p.status()) && Math.abs(p.amount() - order.total) < 0.01)
                .findFirst();

        if (PAGO_PENDIENTE.equals(order.estado)) {
            if (approved.isPresent()) {
                order.mpPaymentId = approved.get().id();
                confirmar(order);
                return;
            }
            if (payments.stream().anyMatch(p -> "approved".equals(p.status()))) {
                LOG.error("La venta {} tiene un pago aprobado con un monto distinto al del pedido ({}): no se confirma", order.id, order.total);
            }
            Instant limite = order.fecha.plus(paymentSettings.pendingExpiryMinutes(), ChronoUnit.MINUTES);
            if (!Instant.now().isBefore(limite)) {
                cancelar(order, "El pago no se completó a tiempo");
            }
        } else if (CANCELADA.equals(order.estado) && approved.isPresent() && order.mpPaymentId == null) {
            // El cliente pagó después de que el pedido se canceló: se le devuelve el dinero
            try {
                mercadoPago.refund(approved.get().id());
                order.mpPaymentId = approved.get().id();
                order.estadoPago = "REEMBOLSADO";
                orderRepository.save(order);
            } catch (MercadoPagoException e) {
                LOG.error("Pago {} aprobado para la venta cancelada {}: hay que reembolsarlo a mano ({})", approved.get().id(), order.id, e.getMessage());
            }
        }
    }

    /** Aviso de MercadoPago (webhook) por un pago: se consulta el pago a MercadoPago y se actualiza el pedido que corresponda. */
    public void procesarAvisoMercadoPago(String paymentId) throws MercadoPagoException {
        Optional<MercadoPagoClient.Payment> payment = mercadoPago.payment(paymentId);
        if (payment.isEmpty() || payment.get().externalReference().isBlank()) {
            return;
        }
        try {
            long orderId = Long.parseLong(payment.get().externalReference());
            orderRepository.findById(orderId).ifPresent(this::sincronizarPago);
        } catch (NumberFormatException e) {
            LOG.debug("Aviso de MercadoPago con referencia que no es de un pedido: {}", payment.get().externalReference());
        }
    }

    private void fallarDefinitivamente(Order order) {
        publisher.publishEvent(new VentaEvents.PagoFallido(order.id, order.userId, order.idTransaccion, order.intentosPago));
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
        Future<AuthResult> future;
        try {
            future = paymentExecutor.submit(() -> gateway.autorizar(order.idTransaccion, order.total, order.medioPagoToken));
        } catch (RejectedExecutionException e) {
            // Demasiadas llamadas a la pasarela en curso: la venta queda pendiente y el worker reintenta
            LOG.warn("Pasarela saturada: no se envió la autorización (idTransaccion={})", order.idTransaccion);
            return new AuthOutcome(AuthKind.SIN_RESPUESTA, null);
        }
        try {
            AuthResult result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            return result.estado() == PaymentStatus.APROBADO
                    ? new AuthOutcome(AuthKind.APROBADO, null)
                    : new AuthOutcome(AuthKind.RECHAZADO, result.motivo());
        } catch (TimeoutException e) {
            // Se interrumpe la llamada: no queda un hilo esperando a una pasarela que no contesta
            future.cancel(true);
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
        Future<Optional<AuthResult>> future;
        try {
            future = paymentExecutor.submit(() -> gateway.consultarPago(idTransaccion));
        } catch (RejectedExecutionException e) {
            return Optional.empty();
        }
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return Optional.empty();
        } catch (ExecutionException e) {
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
    public List<CartItem> cartOf(String userId) {
        List<CartItem> items = isGuest(userId)
                ? new ArrayList<>(store.carts.getOrDefault(userId, List.of()))
                : cartItemRepository.findByUserId(userId);
        // El checkout cobra el precio vigente (promoción incluida), no el que quedó guardado cuando se agregó al carrito.
        for (CartItem item : items) {
            productRepository.findById(item.productId).ifPresent(product -> item.price = Pricing.effectivePrice(product));
        }
        return items;
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

    /**
     * Reserva todo el carrito o nada: si algún producto no alcanza, devuelve lo que ya había reservado y responde el nombre
     * de ese producto; si se reservó todo, null.
     */
    private String reservarStock(List<CartItem> cart) {
        List<CartItem> reservados = new ArrayList<>();
        for (CartItem item : cart) {
            if (!reservarUno(item.productId, item.variantId, item.quantity)) {
                reservados.forEach(r -> liberarUno(r.productId, r.variantId, r.quantity));
                return productRepository.findById(item.productId).map(p -> p.name).orElse(item.productId);
            }
            reservados.add(item);
        }
        return null;
    }

    private void liberarStock(Order order) {
        for (Order.OrderItem item : order.items) {
            liberarUno(item.productId, item.variantId, item.quantity);
        }
    }

    /** Descuenta del producto (y de su variante, si tiene) con sentencias atómicas; false si no alcanzaba el stock. */
    private boolean reservarUno(String productId, Long variantId, int cantidad) {
        if (productRepository.reservar(productId, cantidad) == 0) {
            return false;
        }
        if (variantId != null && variantRepository.reservar(variantId, cantidad) == 0) {
            productRepository.liberar(productId, cantidad);
            return false;
        }
        return true;
    }

    private void liberarUno(String productId, Long variantId, int cantidad) {
        productRepository.liberar(productId, cantidad);
        if (variantId != null) {
            variantRepository.liberar(variantId, cantidad);
        }
    }

    private static VentaOutcome problem(int status, String slug, String title, String detail) {
        return new VentaOutcome(status, null, slug, title, detail);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
