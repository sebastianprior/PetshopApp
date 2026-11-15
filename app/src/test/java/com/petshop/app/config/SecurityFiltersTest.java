package com.petshop.app.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** Headers de seguridad en todas las respuestas y rechazo de HTML en lo que escribe el usuario. */
class SecurityFiltersTest {

    private final SecurityHeadersFilter headers = new SecurityHeadersFilter();
    private final HtmlInputFilter html = new HtmlInputFilter();

    // ---- Headers --------------------------------------------------------------------------------------------------

    private MockHttpServletResponse get(String path, String authToken) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);
        if (authToken != null) {
            request.addHeader("X-Auth-Token", authToken);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        headers.doFilter(request, response, chain);
        assertThat(chain.getRequest()).as("la petición sigue su camino").isNotNull();
        return response;
    }

    @Test
    void everyApiResponseCarriesTheSecurityHeaders() throws Exception {
        MockHttpServletResponse response = get("/api/products", null);

        assertThat(response.getHeader("Content-Security-Policy")).isEqualTo(SecurityHeadersFilter.API_CSP)
                .contains("default-src 'none'").contains("frame-ancestors 'none'");
        assertThat(response.getHeader("Strict-Transport-Security")).isEqualTo("max-age=31536000; includeSubDomains");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
        assertThat(response.getHeader("Permissions-Policy")).contains("camera=()").contains("geolocation=()");
    }

    @Test
    void theApiPolicyAllowsNoScriptsAtAll() throws Exception {
        assertThat(get("/api/products", null).getHeader("Content-Security-Policy")).doesNotContain("script-src").doesNotContain("unsafe");
    }

    @Test
    void theSwaggerDocumentationGetsItsOwnPolicyThatAllowsItsScripts() throws Exception {
        for (String path : new String[]{"/swagger-ui/index.html", "/swagger-ui.html", "/v3/api-docs", "/v3/api-docs/swagger-config"}) {
            assertThat(get(path, null).getHeader("Content-Security-Policy")).as(path).isEqualTo(SecurityHeadersFilter.DOCS_CSP).contains("script-src 'self'");
        }
        assertThat(get("/api/v3/api-docs", null).getHeader("Content-Security-Policy")).isEqualTo(SecurityHeadersFilter.API_CSP);
    }

    @Test
    void responsesThatDependOnTheSessionAreNeverStored() throws Exception {
        assertThat(get("/api/orders/me", "un-token").getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(get("/api/auth/login", null).getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(get("/api/auth/me", null).getHeader("Pragma")).isEqualTo("no-cache");
    }

    @Test
    void publicCatalogResponsesAreLeftCacheable() throws Exception {
        assertThat(get("/api/products", null).getHeader("Cache-Control")).isNull();
    }

    // ---- HTML en lo que escribe el usuario ----------------------------------------------------------------------------

    private MockHttpServletResponse post(String json, String contentType, MockFilterChain chain) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");
        request.setContentType(contentType);
        request.setCharacterEncoding("UTF-8");
        request.setContent(json.getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        html.doFilter(request, response, chain);
        return response;
    }

    private boolean rejected(String json) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = post(json, "application/json", chain);
        boolean blocked = chain.getRequest() == null;
        if (blocked) {
            assertThat(response.getStatus()).isEqualTo(400);
        }
        return blocked;
    }

    @Test
    void aScriptTagInAnyTextIsRejectedWithAProblemDetailsBody() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = post("{\"email\":\"a@b.com\",\"name\":\"<script>alert(1)</script>\"}", "application/json", chain);

        assertThat(chain.getRequest()).as("el controlador no llega a ejecutarse").isNull();
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                .contains("\"status\":400").contains("urn:petshop:problem:entrada-invalida").contains("El campo 'name' no puede contener etiquetas HTML.")
                .doesNotContain("script");
    }

    @Test
    void htmlHiddenInsideNestedObjectsAndListsIsRejectedToo() throws Exception {
        assertThat(rejected("{\"items\":[{\"nombre\":\"<img src=x onerror=alert(1)>\"}]}")).isTrue();
        assertThat(rejected("{\"a\":{\"b\":{\"c\":[\"ok\",\"</div>\"]}}}")).isTrue();
        assertThat(rejected("[\"<b>negrita</b>\"]")).isTrue();
    }

    @Test
    void everyKindOfTagStartIsDetected() throws Exception {
        for (String text : new String[]{"<script>", "</div>", "<!-- x -->", "<?php echo 1 ?>", "hola <b>", "<A href=x>"}) {
            assertThat(rejected("{\"x\":\"" + text + "\"}")).as(text).isTrue();
        }
    }

    @Test
    void ordinaryTextWithSymbolsIsAccepted() throws Exception {
        for (String text : new String[]{"Fernández & Hijos", "5 < 10", "a<5", "<3", "me gusta > que < esto 1", "< script> con espacio no es una etiqueta", "Av. Libertador 1234, 3°B", "Ñandú \\\"el grande\\\""}) {
            assertThat(rejected("{\"x\":\"" + text + "\"}")).as(text).isFalse();
        }
    }

    @Test
    void passwordsAndTokensMayContainAnyCharacter() throws Exception {
        assertThat(rejected("{\"email\":\"a@b.com\",\"password\":\"x<script>y\"}")).isFalse();
        assertThat(rejected("{\"currentPassword\":\"<a>\",\"newPassword\":\"<b>\"}")).isFalse();
        assertThat(rejected("{\"refreshToken\":\"<a>\",\"idToken\":\"<b>\"}")).isFalse();
    }

    @Test
    void theControllerStillReadsTheWholeBodyAfterTheCheck() throws Exception {
        String json = "{\"name\":\"Ñandú Pérez\",\"email\":\"ñ@b.com\"}";
        MockFilterChain chain = new MockFilterChain();

        post(json, "application/json", chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(new String(chain.getRequest().getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(json);
        MockFilterChain again = new MockFilterChain();
        post(json, "application/json", again);
        assertThat(again.getRequest().getReader().readLine()).isEqualTo(json);
    }

    @Test
    void brokenJsonIsLeftForTheControllerToReport() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        post("{ esto no es json <script>", "application/json", chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void onlyJsonWritesAreChecked() throws Exception {
        MockFilterChain soap = new MockFilterChain();
        post("<Envelope><x>hola</x></Envelope>", "text/xml", soap);
        assertThat(soap.getRequest()).isNotNull();

        MockHttpServletRequest getRequest = new MockHttpServletRequest("GET", "/api/products");
        getRequest.setContentType("application/json");
        getRequest.setContent("{\"x\":\"<script>\"}".getBytes(StandardCharsets.UTF_8));
        MockFilterChain getChain = new MockFilterChain();
        html.doFilter(getRequest, new MockHttpServletResponse(), getChain);
        assertThat(getChain.getRequest()).isNotNull();

        MockHttpServletRequest noType = new MockHttpServletRequest("POST", "/api/x");
        MockFilterChain noTypeChain = new MockFilterChain();
        html.doFilter(noType, new MockHttpServletResponse(), noTypeChain);
        assertThat(noTypeChain.getRequest()).isNotNull();
    }

    @Test
    void putAndPatchAreCheckedLikePost() throws Exception {
        for (String method : new String[]{"PUT", "PATCH"}) {
            MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/auth/me");
            request.setContentType("application/json");
            request.setContent("{\"name\":\"<b>x</b>\"}".getBytes(StandardCharsets.UTF_8));
            MockFilterChain chain = new MockFilterChain();
            MockHttpServletResponse response = new MockHttpServletResponse();

            html.doFilter(request, response, chain);

            assertThat(chain.getRequest()).as(method).isNull();
            assertThat(response.getStatus()).as(method).isEqualTo(400);
        }
    }

    @Test
    void theFieldNameInTheMessageCannotCarryMarkup() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = post("{\"na<script>me\":\"<b>x</b>\"}", "application/json", chain);

        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).doesNotContain("<").doesNotContain(">");
    }
}
