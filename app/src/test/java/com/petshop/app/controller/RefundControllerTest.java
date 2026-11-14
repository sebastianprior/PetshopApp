package com.petshop.app.controller;

import com.petshop.app.model.Product;
import com.petshop.app.model.Refund;
import com.petshop.app.model.Return;
import com.petshop.app.model.User;
import com.petshop.app.repository.ProductRepository;
import com.petshop.app.repository.RefundRepository;
import com.petshop.app.repository.ReturnRepository;
import com.petshop.app.repository.UserRepository;
import com.petshop.app.service.AdminGuard;
import com.petshop.app.service.JwtUtil;
import com.petshop.app.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Reembolsar una devolución aprobada: solo el administrador, una sola vez y por el monto del producto. */
class RefundControllerTest {

    private RefundRepository refunds;
    private ReturnRepository returns;
    private ProductRepository products;
    private UserRepository users;
    private NotificationService notifications;
    private RefundController controller;
    private String adminToken;
    private String customerToken;

    @BeforeEach
    void setUp() {
        refunds = mock(RefundRepository.class);
        returns = mock(ReturnRepository.class);
        products = mock(ProductRepository.class);
        users = mock(UserRepository.class);
        notifications = mock(NotificationService.class);
        JwtUtil jwtUtil = new JwtUtil("test-secret-test-secret-test-secret-test-secret", 60_000);
        controller = new RefundController(refunds, returns, products, users, notifications, new AdminGuard(jwtUtil));
        adminToken = jwtUtil.generateToken("admin-1", "admin@example.com", "ADMIN");
        customerToken = jwtUtil.generateToken("user-1", "cliente@example.com", "CUSTOMER");
    }

    private Return returnWith(Return.Status status, int quantity) {
        Return r = new Return("user-1", "p1", quantity, "No le gustó", status, Instant.now());
        r.id = 5L;
        when(returns.findById(5L)).thenReturn(Optional.of(r));
        return r;
    }

    @Test
    void aCustomerCannotProcessARefund() {
        returnWith(Return.Status.APROBADA, 1);

        for (String token : new String[]{null, "token-roto", customerToken}) {
            assertThat(controller.process(token, 5L).getStatusCode().value()).isEqualTo(customerToken.equals(token) ? 403 : 401);
        }
        verify(refunds, never()).save(any());
        verify(returns, never()).save(any());
    }

    @Test
    void aReturnThatDoesNotExistIsRejected() {
        when(returns.findById(99L)).thenReturn(Optional.empty());

        assertThat(controller.process(adminToken, 99L).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void onlyAnApprovedReturnCanBeRefunded() {
        for (Return.Status status : new Return.Status[]{Return.Status.PENDIENTE, Return.Status.RECHAZADA, Return.Status.PROCESADO}) {
            returnWith(status, 1);

            ResponseEntity<?> response = controller.process(adminToken, 5L);

            assertThat(response.getStatusCode().value()).as("estado " + status).isEqualTo(409);
        }
        verify(refunds, never()).save(any());
    }

    @Test
    void theSameReturnCannotBeRefundedTwice() {
        returnWith(Return.Status.APROBADA, 1);
        when(refunds.findByReturnId(5L)).thenReturn(Optional.of(new Refund(5L, 100, Refund.Status.PROCESADO, Instant.now())));

        ResponseEntity<?> response = controller.process(adminToken, 5L);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        verify(refunds, never()).save(any());
        verify(notifications, never()).notify(anyString(), anyString());
    }

    @Test
    void refundsThePriceTimesTheQuantityMarksTheReturnAsProcessedAndNotifiesTheCustomer() {
        Return approved = returnWith(Return.Status.APROBADA, 3);
        when(refunds.findByReturnId(5L)).thenReturn(Optional.empty());
        when(products.findById("p1")).thenReturn(Optional.of(
                new Product("p1", "Collar", "x", 899.0, null, 4.0, "/products/p1.svg", null, "accesorios", 10)));
        when(users.findById("user-1")).thenReturn(Optional.of(new User("user-1", "cliente@ejemplo.com", "x", "Cliente")));

        ResponseEntity<?> response = controller.process(adminToken, 5L);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        ArgumentCaptor<Refund> saved = ArgumentCaptor.forClass(Refund.class);
        verify(refunds).save(saved.capture());
        assertThat(saved.getValue().monto).isEqualTo(2697.0);
        assertThat(saved.getValue().returnId).isEqualTo(5L);
        assertThat(saved.getValue().estado).isEqualTo(Refund.Status.PROCESADO);
        assertThat(approved.estado).isEqualTo(Return.Status.PROCESADO);
        verify(returns).save(approved);
        verify(notifications).notify("cliente@ejemplo.com", "Tu reembolso de $2697.0 fue procesado.");
    }

    @Test
    void ifTheProductWasDeletedTheRefundIsZeroInsteadOfFailing() {
        returnWith(Return.Status.APROBADA, 2);
        when(refunds.findByReturnId(5L)).thenReturn(Optional.empty());
        when(products.findById("p1")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.process(adminToken, 5L);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        ArgumentCaptor<Refund> saved = ArgumentCaptor.forClass(Refund.class);
        verify(refunds).save(saved.capture());
        assertThat(saved.getValue().monto).isZero();
    }

    @Test
    void ifTheCustomerNoLongerExistsTheRefundIsStillProcessedWithoutNotifying() {
        returnWith(Return.Status.APROBADA, 1);
        when(refunds.findByReturnId(5L)).thenReturn(Optional.empty());
        when(users.findById("user-1")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.process(adminToken, 5L);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(notifications, never()).notify(anyString(), anyString());
    }
}
