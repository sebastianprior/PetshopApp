package com.petshop.app.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Agrega a todas las respuestas los headers de seguridad que piden OWASP ASVS y la rúbrica de testing:
 * <ul>
 *   <li><b>Content-Security-Policy</b>: la API solo devuelve JSON/XML, así que no necesita cargar nada (default-src 'none')
 *       ni puede ser embebida. La documentación de Swagger necesita sus propios scripts y recibe una política propia.</li>
 *   <li><b>Strict-Transport-Security</b>: los navegadores lo ignoran por HTTP y lo respetan por HTTPS (el que usa el
 *       servidor publicado), así que se envía siempre.</li>
 *   <li><b>X-Content-Type-Options</b>, <b>X-Frame-Options</b>, <b>Referrer-Policy</b> y <b>Permissions-Policy</b>.</li>
 *   <li><b>Cache-Control: no-store</b> en lo que depende de la sesión, para que no quede en cachés compartidas.</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    static final String API_CSP = "default-src 'none'; img-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";
    static final String DOCS_CSP = "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; "
            + "script-src 'self' 'unsafe-inline'; frame-ancestors 'none'; base-uri 'self'";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean docs = path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs");

        response.setHeader("Content-Security-Policy", docs ? DOCS_CSP : API_CSP);
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()");

        boolean personal = request.getHeader("X-Auth-Token") != null || path.startsWith("/api/auth/");
        if (personal) {
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("Pragma", "no-cache");
        }
        chain.doFilter(request, response);
    }
}
