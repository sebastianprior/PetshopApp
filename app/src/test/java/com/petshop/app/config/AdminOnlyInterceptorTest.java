package com.petshop.app.config;

import com.petshop.app.controller.CouponController;
import com.petshop.app.controller.OrderController;
import com.petshop.app.controller.ProductController;
import com.petshop.app.controller.UploadController;
import com.petshop.app.controller.UserController;
import com.petshop.app.service.AdminGuard;
import com.petshop.app.service.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** El rol de administrador se controla antes de leer el cuerpo: 401 sin sesión válida, 403 con la sesión de un cliente. */
class AdminOnlyInterceptorTest {

    /** Controlador de ejemplo con una operación de administrador y una pública. */
    static class Sample {
        @AdminOnly
        public void adminThing() {}

        public void publicThing() {}
    }

    private final JwtUtil jwtUtil = new JwtUtil("test-secret-test-secret-test-secret-test-secret", 60_000);
    private AdminOnlyInterceptor interceptor;
    private HandlerMethod adminHandler;
    private HandlerMethod publicHandler;

    @BeforeEach
    void setUp() throws Exception {
        interceptor = new AdminOnlyInterceptor(new AdminGuard(jwtUtil));
        Sample sample = new Sample();
        adminHandler = new HandlerMethod(sample, Sample.class.getMethod("adminThing"));
        publicHandler = new HandlerMethod(sample, Sample.class.getMethod("publicThing"));
    }

    /** Lo que hizo el interceptor: si deja seguir y qué respondió si cortó. */
    private record Result(boolean proceed, MockHttpServletResponse response) {}

    private Result call(Object handler, String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/coupons");
        if (token != null) {
            request.addHeader("X-Auth-Token", token);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        return new Result(interceptor.preHandle(request, response, handler), response);
    }

    @Test
    void anAdministratorGoesThrough() throws Exception {
        Result result = call(adminHandler, jwtUtil.generateToken("u2", "a@e.com", "ADMIN"));

        assertThat(result.proceed()).as("sigue su camino").isTrue();
        assertThat(result.response().getStatus()).isEqualTo(200);
        assertThat(result.response().getContentAsString()).isEmpty();
    }

    @Test
    void aCustomerIsForbiddenBeforeAnyBodyIsRead() throws Exception {
        Result result = call(adminHandler, jwtUtil.generateToken("u1", "c@e.com", "CUSTOMER"));

        assertThat(result.proceed()).as("se corta acá").isFalse();
        assertThat(result.response().getStatus()).isEqualTo(403);
        assertThat(result.response().getContentType()).startsWith("application/json");
        assertThat(result.response().getContentAsString(StandardCharsets.UTF_8)).isEqualTo("{\"error\":\"Requiere permisos de administrador\"}");
    }

    @Test
    void withoutAValidSessionTheAnswerIsUnauthorized() throws Exception {
        for (String token : new String[]{null, "", "basura", "a.b.c"}) {
            Result result = call(adminHandler, token);

            assertThat(result.proceed()).as("token=" + token).isFalse();
            assertThat(result.response().getStatus()).as("token=" + token).isEqualTo(401);
            assertThat(result.response().getContentAsString(StandardCharsets.UTF_8)).contains("Iniciá sesión");
        }
    }

    @Test
    void operationsNotMarkedAsAdminOnlyAreNotAffected() throws Exception {
        for (String token : new String[]{null, "basura", jwtUtil.generateToken("u1", "c@e.com", "CUSTOMER")}) {
            Result result = call(publicHandler, token);

            assertThat(result.proceed()).isTrue();
            assertThat(result.response().getStatus()).isEqualTo(200);
        }
    }

    @Test
    void handlersThatAreNotControllerMethodsAreNotAffected() throws Exception {
        assertThat(call(new Object(), null).proceed()).isTrue();
    }

    @Test
    void theRealAdminOperationsAreMarked() throws Exception {
        assertThat(marked(ProductController.class, "create", "update", "delete")).isTrue();
        assertThat(marked(CouponController.class, "list", "create", "update", "delete")).isTrue();
        assertThat(marked(OrderController.class, "listAll", "stats", "updateStatus")).isTrue();
        assertThat(marked(UserController.class, "list", "updateRole", "updateStatus")).isTrue();
        assertThat(marked(UploadController.class, "upload")).isTrue();
    }

    @Test
    void theCustomerFacingOperationsAreNotMarked() {
        assertThat(marked(ProductController.class, "list", "get")).isFalse();
        assertThat(marked(CouponController.class, "validateCode")).isFalse();
        assertThat(marked(OrderController.class, "listMine")).isFalse();
    }

    /** true si TODAS las operaciones con esos nombres llevan @AdminOnly (si no existe ninguna, falla). */
    private static boolean marked(Class<?> controller, String... names) {
        int found = 0;
        for (Method m : controller.getDeclaredMethods()) {
            if (Arrays.asList(names).contains(m.getName())) {
                found++;
                if (!m.isAnnotationPresent(AdminOnly.class)) {
                    return false;
                }
            }
        }
        return found > 0;
    }
}
