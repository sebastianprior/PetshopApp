package com.petshop.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petshop.app.service.AdminGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * Controla las operaciones marcadas con {@link AdminOnly} antes de que Spring lea el cuerpo del pedido. Sin esto, un
 * cuerpo mal formado o incompleto daba 400 antes de llegar a verificar el rol, y la respuesta debía ser siempre 401
 * (sin sesión válida) o 403 (sesión de cliente).
 */
@Component
public class AdminOnlyInterceptor implements HandlerInterceptor {

    private final AdminGuard adminGuard;
    private final ObjectMapper mapper = new ObjectMapper();

    public AdminOnlyInterceptor(AdminGuard adminGuard) {
        this.adminGuard = adminGuard;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod method) || !method.hasMethodAnnotation(AdminOnly.class)) {
            return true;
        }
        String token = request.getHeader("X-Auth-Token");
        if (adminGuard.isAdmin(token)) {
            return true;
        }
        ResponseEntity<?> denied = adminGuard.denied(token);
        response.setStatus(denied.getStatusCode().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        mapper.writeValue(response.getOutputStream(), denied.getBody());
        return false;
    }
}
