package com.petshop.app.jakarta;

import com.petshop.app.controller.ProductController;
import com.petshop.app.model.Order;
import com.petshop.app.repository.OrderRepository;
import com.petshop.app.service.AdminGuard;
import com.petshop.app.service.JwtUtil;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parte de la API v1 expresada con anotaciones de Jakarta REST (@Path, @GET, @Produces) en lugar de Spring MVC:
 * el catálogo público y la consulta del estado de pago de una venta. Misma lógica y mismos errores Problem Details.
 */
@Component
@Path("/v1")
@Produces(MediaType.APPLICATION_JSON)
public class CatalogoYPagoResource {

    private static final String PROBLEM_JSON = "application/problem+json";

    private final ProductController productController;
    private final OrderRepository orderRepository;
    private final JwtUtil jwtUtil;
    private final AdminGuard adminGuard;

    public CatalogoYPagoResource(ProductController productController, OrderRepository orderRepository,
                                 JwtUtil jwtUtil, AdminGuard adminGuard) {
        this.productController = productController;
        this.orderRepository = orderRepository;
        this.jwtUtil = jwtUtil;
        this.adminGuard = adminGuard;
    }

    @GET
    @Path("/productos")
    public Response productos(@QueryParam("category") String category, @QueryParam("sort") String sort,
                              @QueryParam("search") String search) {
        return Response.ok(productController.list(category, sort, search)).build();
    }

    @GET
    @Path("/ventas/{id}/pago")
    public Response pago(@PathParam("id") Long id,
                         @HeaderParam("X-Auth-Token") String token,
                         @HeaderParam("X-Guest-Id") String guestId) {
        boolean autenticado = token != null && jwtUtil.isTokenValid(token);
        String principal = autenticado ? jwtUtil.extractUserId(token)
                : (guestId != null && !guestId.isBlank() ? "guest:" + guestId.trim() : null);
        if (principal == null) {
            return problem(401, "no-autorizado", "No autorizado", "Falta un token válido en X-Auth-Token.");
        }

        Order order = orderRepository.findById(id).orElse(null);
        boolean visible = order != null && (principal.equals(order.userId) || (autenticado && adminGuard.isAdmin(token)));
        if (!visible) {
            return problem(404, "no-encontrado", "No encontrado", "No existe la venta " + id + ".");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ventaId", order.id);
        body.put("idTransaccion", order.idTransaccion);
        body.put("estadoPago", order.estadoPago);
        body.put("estadoVenta", order.estado);
        body.put("intentos", order.intentosPago);
        body.put("monto", order.total);
        return Response.ok(body).build();
    }

    private Response problem(int status, String slug, String title, String detail) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "urn:petshop:problem:" + slug);
        body.put("title", title);
        body.put("status", status);
        body.put("detail", detail);
        return Response.status(status).type(PROBLEM_JSON).entity(body).build();
    }
}
