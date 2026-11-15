package com.petshop.app.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Rechaza (400, Problem Details) los cuerpos JSON que traen etiquetas HTML dentro de un texto (por ejemplo un nombre
 * con {@code <script>alert(1)</script>}). Ningún dato de la tienda necesita HTML, así que no se guarda: de esa forma no
 * puede llegar después a un email, un reporte o un panel que no lo escape. Los campos de contraseña y los tokens no se
 * revisan porque pueden llevar cualquier carácter.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class HtmlInputFilter extends OncePerRequestFilter {

    /**
     * Un "<" pegado a una letra, "/", "!" o "?" es el comienzo de una etiqueta, comentario o instrucción. Con un espacio
     * en el medio ("a < b") el navegador lo toma como texto, así que se acepta.
     */
    private static final Pattern TAG_START = Pattern.compile("<[/!?a-zA-Z]");
    private static final Set<String> UNCHECKED_FIELDS = Set.of(
            "password", "currentPassword", "newPassword", "token", "refreshToken", "credential", "idToken", "medioPago");
    private static final int MAX_BYTES = 1024 * 1024;

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        boolean writes = "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
        String type = request.getContentType();
        return !writes || type == null || !type.toLowerCase().contains("json");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        byte[] body = request.getInputStream().readAllBytes();
        // Un JSON de la tienda nunca es tan grande: si lo es, no se revisa y lo resuelve quien corresponda
        String field = body.length <= MAX_BYTES ? firstFieldWithHtml(body) : null;
        if (field != null) {
            reject(response, field);
            return;
        }
        chain.doFilter(new ReplayableRequest(request, body), response);
    }

    private String firstFieldWithHtml(byte[] body) {
        JsonNode root;
        try {
            root = mapper.readTree(body);
        } catch (IOException e) {
            return null; // JSON roto: lo informa el controlador
        }
        return root == null ? null : find(root, "");
    }

    private String find(JsonNode node, String name) {
        if (node.isTextual()) {
            return !UNCHECKED_FIELDS.contains(name) && TAG_START.matcher(node.asText()).find() ? (name.isEmpty() ? "el texto" : name) : null;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String found = find(entry.getValue(), entry.getKey());
                if (found != null) {
                    return found;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                String found = find(item, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private void reject(HttpServletResponse response, String field) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        mapper.writeValue(response.getOutputStream(), Map.of(
                "type", "urn:petshop:problem:entrada-invalida",
                "title", "Entrada inválida",
                "status", 400,
                "detail", "El campo '" + field.replaceAll("[^\\p{L}\\p{N}_ .-]", "") + "' no puede contener etiquetas HTML."));
    }

    /** Vuelve a ofrecer el cuerpo ya leído, para que el controlador lo pueda leer de nuevo. */
    private static final class ReplayableRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        ReplayableRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return in.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public int read() {
                    return in.read();
                }

                @Override
                public int read(byte[] b, int off, int len) {
                    return in.read(b, off, len);
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            Charset charset = encoding != null ? Charset.forName(encoding) : StandardCharsets.UTF_8;
            return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(body), charset));
        }
    }
}
