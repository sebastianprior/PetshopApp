package com.petshop.app.service;

import com.petshop.app.dto.VentaRequest;
import com.petshop.app.model.CartItem;
import com.petshop.app.model.IdempotencyRecord;
import com.petshop.app.model.Order;
import com.petshop.app.model.Product;
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
import com.petshop.app.service.VentaService.VentaOutcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VentaServiceTest {

    private OrderRepository orderRepository;
    private CartItemRepository cartItemRepository;
    private ProductRepository productRepository;
    private PaymentGateway gateway;
    private ApplicationEventPublisher publisher;
    private ExecutorService executor;
    private VentaService service;

    private Product product;
    private final Map<String, IdempotencyRecord> idempotencyStore = new HashMap<>();
    private final Map<Long, Order> orders = new HashMap<>();
    private final AtomicLong ids = new AtomicLong(1);
    private final List<CartItem> cart = new ArrayList<>();

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        cartItemRepository = mock(CartItemRepository.class);
        productRepository = mock(ProductRepository.class);
        ProductVariantRepository variantRepository = mock(ProductVariantRepository.class);
        CouponRepository couponRepository = mock(CouponRepository.class);
        IdempotencyRecordRepository idempotencyRepository = mock(IdempotencyRecordRepository.class);
        gateway = mock(PaymentGateway.class);
        publisher = mock(ApplicationEventPublisher.class);
        executor = Executors.newCachedThreadPool();

        product = new Product("p1", "Collar", "Marca", 1000.0, null, 4.5, "/img.jpg", null, "accesorios", 10);
        when(productRepository.findById("p1")).thenReturn(Optional.of(product));

        cart.clear();
        cart.add(new CartItem("p1", "Collar", "accesorios", 2, 1000.0));
        when(cartItemRepository.findByUserId("user-1")).thenAnswer(inv -> new ArrayList<>(cart));

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order saved = inv.getArgument(0);
            if (saved.id == null) {
                saved.id = ids.getAndIncrement();
            }
            orders.put(saved.id, saved);
            return saved;
        });
        when(orderRepository.findById(any(Long.class))).thenAnswer(inv -> Optional.ofNullable(orders.get(inv.<Long>getArgument(0))));
        when(orderRepository.findByEstado(anyString())).thenAnswer(inv ->
                orders.values().stream().filter(o -> inv.getArgument(0).equals(o.estado)).toList());

        when(idempotencyRepository.findById(anyString())).thenAnswer(inv ->
                Optional.ofNullable(idempotencyStore.get(inv.<String>getArgument(0))));
        when(idempotencyRepository.save(any(IdempotencyRecord.class))).thenAnswer(inv -> {
            IdempotencyRecord record = inv.getArgument(0);
            idempotencyStore.put(record.idemKey, record);
            return record;
        });

        service = new VentaService(orderRepository, cartItemRepository, productRepository, variantRepository,
                couponRepository, idempotencyRepository, gateway, executor, publisher, 200, 3, 0);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    private VentaRequest request() {
        VentaRequest req = new VentaRequest();
        req.nombre = "Cliente";
        req.direccion = "Calle 1";
        req.ciudad = "CABA";
        req.medioPago = "tok_aprobado";
        return req;
    }

    private void gatewayApproves() {
        when(gateway.autorizar(anyString(), anyDouble(), any())).thenReturn(new AuthResult(PaymentStatus.APROBADO, null));
    }

    private void gatewayTimesOut() {
        when(gateway.autorizar(anyString(), anyDouble(), any())).thenAnswer(inv -> {
            Thread.sleep(1500);
            return new AuthResult(PaymentStatus.APROBADO, null);
        });
    }

    @Test
    void approvedPaymentConfirmsTheSale() {
        gatewayApproves();

        VentaOutcome outcome = service.crear("user-1", "key-1", request());

        assertThat(outcome.status()).isEqualTo(201);
        assertThat(outcome.order().estado).isEqualTo("COMPLETADA");
        assertThat(outcome.order().estadoPago).isEqualTo("APROBADO");
        assertThat(outcome.order().total).isEqualTo(3500.0);
        assertThat(product.stock).isEqualTo(8);
        verify(cartItemRepository).deleteAll(any());
        verify(publisher).publishEvent(any(VentaEvents.VentaConfirmada.class));
    }

    @Test
    void rejectedPaymentReturns402AndReleasesStock() {
        when(gateway.autorizar(anyString(), anyDouble(), any()))
                .thenReturn(new AuthResult(PaymentStatus.RECHAZADO, "Fondos insuficientes"));

        VentaOutcome outcome = service.crear("user-1", "key-2", request());

        assertThat(outcome.status()).isEqualTo(402);
        assertThat(outcome.problemSlug()).isEqualTo("pago-rechazado");
        assertThat(product.stock).isEqualTo(10);
        verify(orderRepository).delete(any(Order.class));
    }

    @Test
    void gatewayFaultPg400Returns422AndReleasesStock() {
        when(gateway.autorizar(anyString(), anyDouble(), any()))
                .thenThrow(new GatewayFaultException(GatewayFaultException.INVALID_DATA, "Datos de pago inválidos"));

        VentaOutcome outcome = service.crear("user-1", "key-3", request());

        assertThat(outcome.status()).isEqualTo(422);
        assertThat(outcome.problemSlug()).isEqualTo("pago-datos-invalidos");
        assertThat(product.stock).isEqualTo(10);
    }

    @Test
    void gatewayTimeoutReturns202AndKeepsStockReserved() {
        gatewayTimesOut();

        VentaOutcome outcome = service.crear("user-1", "key-4", request());

        assertThat(outcome.status()).isEqualTo(202);
        assertThat(outcome.order().estado).isEqualTo("PAGO_PENDIENTE");
        assertThat(outcome.order().idTransaccion).isNotBlank();
        assertThat(product.stock).isEqualTo(8);
        verify(cartItemRepository).deleteAll(any());
    }

    @Test
    void sameIdempotencyKeyAndBodyReplaysTheOriginalResult() {
        gatewayApproves();

        VentaOutcome first = service.crear("user-1", "key-5", request());
        VentaOutcome second = service.crear("user-1", "key-5", request());

        assertThat(second.status()).isEqualTo(201);
        assertThat(second.order().id).isEqualTo(first.order().id);
        verify(gateway, times(1)).autorizar(anyString(), anyDouble(), any());
        assertThat(product.stock).isEqualTo(8);
    }

    @Test
    void sameIdempotencyKeyWithDifferentBodyReturns409() {
        gatewayApproves();
        service.crear("user-1", "key-6", request());

        VentaRequest other = request();
        other.direccion = "Otra calle 99";
        VentaOutcome outcome = service.crear("user-1", "key-6", other);

        assertThat(outcome.status()).isEqualTo(409);
        assertThat(outcome.problemSlug()).isEqualTo("idempotency-key-reutilizada");
    }

    @Test
    void workerConfirmsAPendingSaleWhenTheGatewayAlreadyHasTheTransaction() {
        gatewayTimesOut();
        VentaOutcome pending = service.crear("user-1", "key-7", request());
        when(gateway.consultarPago(pending.order().idTransaccion))
                .thenReturn(Optional.of(new AuthResult(PaymentStatus.APROBADO, null)));

        service.procesarPendientes();

        assertThat(pending.order().estado).isEqualTo("COMPLETADA");
        assertThat(pending.order().estadoPago).isEqualTo("APROBADO");
        verify(gateway, times(1)).autorizar(anyString(), anyDouble(), any());
        verify(publisher).publishEvent(any(VentaEvents.VentaConfirmada.class));
    }

    @Test
    void workerCancelsTheSaleAndReleasesStockAfterThreeFailedAttempts() {
        gatewayTimesOut();
        when(gateway.consultarPago(anyString())).thenReturn(Optional.empty());
        VentaOutcome pending = service.crear("user-1", "key-8", request());

        service.procesarPendientes();
        assertThat(pending.order().estado).isEqualTo("PAGO_PENDIENTE");
        assertThat(pending.order().intentosPago).isEqualTo(2);

        service.procesarPendientes();

        assertThat(pending.order().estado).isEqualTo("CANCELADA");
        assertThat(pending.order().intentosPago).isEqualTo(3);
        assertThat(product.stock).isEqualTo(10);
        verify(publisher).publishEvent(any(VentaEvents.VentaCancelada.class));
    }

    @Test
    void cancellingACompletedSaleReleasesStockAndMarksTheRefund() {
        gatewayApproves();
        Order order = service.crear("user-1", "key-9", request()).order();

        VentaOutcome outcome = service.cancelarPorSolicitud(order);

        assertThat(outcome.status()).isEqualTo(200);
        assertThat(order.estado).isEqualTo("CANCELADA");
        assertThat(order.estadoPago).isEqualTo("REEMBOLSADO");
        assertThat(product.stock).isEqualTo(10);
    }

    @Test
    void aShippedSaleCannotBeCancelled() {
        gatewayApproves();
        Order order = service.crear("user-1", "key-10", request()).order();
        order.estado = "ENVIADA";

        VentaOutcome outcome = service.cancelarPorSolicitud(order);

        assertThat(outcome.status()).isEqualTo(409);
        assertThat(outcome.problemSlug()).isEqualTo("transicion-invalida");
    }

    @Test
    void validatesPaymentTokenAndShippingBeforeTouchingStock() {
        VentaRequest noToken = request();
        noToken.medioPago = " ";
        VentaRequest noAddress = request();
        noAddress.direccion = null;

        assertThat(service.crear("user-1", "key-11", noToken).problemSlug()).isEqualTo("medio-pago-requerido");
        assertThat(service.crear("user-1", "key-12", noAddress).problemSlug()).isEqualTo("datos-envio-incompletos");
        assertThat(product.stock).isEqualTo(10);
    }
}
