package com.petshop.app.controller;

import com.petshop.app.service.AdminGuard;
import com.petshop.app.service.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Subida de imágenes: solo administrador, solo imágenes, hasta 5 MB y siempre dentro de la carpeta de subidas. */
class UploadControllerTest {

    @TempDir
    Path folder;

    private UploadController controller;
    private String adminToken;
    private String customerToken;

    @BeforeEach
    void setUp() {
        JwtUtil jwtUtil = new JwtUtil("test-secret-test-secret-test-secret-test-secret", 60_000);
        AdminGuard guard = new AdminGuard(jwtUtil);
        controller = new UploadController(guard, folder.resolve("subidas").toString());
        adminToken = jwtUtil.generateToken("admin-1", "admin@example.com", "ADMIN");
        customerToken = jwtUtil.generateToken("user-1", "cliente@ejemplo.com", "CUSTOMER");
    }

    private static MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, "application/octet-stream", content);
    }

    private long storedFiles() throws IOException {
        Path dir = folder.resolve("subidas");
        if (!Files.exists(dir)) {
            return 0;
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.count();
        }
    }

    @Test
    void aCustomerCannotUploadAnything() throws IOException {
        for (String token : new String[]{null, "token-roto", customerToken}) {
            ResponseEntity<?> response = controller.upload(token, file("foto.png", new byte[]{1, 2, 3}));
            assertThat(response.getStatusCode().value()).isEqualTo(403);
        }
        assertThat(storedFiles()).isZero();
    }

    @Test
    void anEmptyFileIsRejected() throws IOException {
        ResponseEntity<?> response = controller.upload(adminToken, file("foto.png", new byte[0]));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isEqualTo(Map.of("error", "Archivo vacío"));
    }

    @Test
    void aFileOverFiveMegabytesIsRejectedButExactlyFiveIsAccepted() throws IOException {
        ResponseEntity<?> tooBig = controller.upload(adminToken, file("grande.png", new byte[5 * 1024 * 1024 + 1]));
        ResponseEntity<?> limit = controller.upload(adminToken, file("limite.png", new byte[5 * 1024 * 1024]));

        assertThat(tooBig.getStatusCode().value()).isEqualTo(400);
        assertThat(tooBig.getBody()).isEqualTo(Map.of("error", "El archivo no puede superar los 5MB"));
        assertThat(limit.getStatusCode().value()).isEqualTo(200);
        assertThat(storedFiles()).isEqualTo(1);
    }

    @Test
    void onlyImageExtensionsAreAccepted() throws IOException {
        for (String name : new String[]{"virus.exe", "pagina.html", "dibujo.svg", "script.js", "sin-extension", "doble.png.exe", ".", ""}) {
            ResponseEntity<?> response = controller.upload(adminToken, file(name, new byte[]{1}));
            assertThat(response.getStatusCode().value()).as(name).isEqualTo(400);
        }
        assertThat(storedFiles()).isZero();
    }

    @Test
    void aFileWithoutAnyNameIsRejected() throws IOException {
        MockMultipartFile noName = new MockMultipartFile("file", null, "image/png", new byte[]{1});

        assertThat(controller.upload(adminToken, noName).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void savesAValidImageWithARandomNameAndReturnsItsAddress() throws IOException {
        ResponseEntity<?> response = controller.upload(adminToken, file("Mi Foto.PNG", new byte[]{1, 2, 3}));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        String url = (String) ((Map<?, ?>) response.getBody()).get("url");
        assertThat(url).matches("/uploads/[0-9a-f-]{36}\\.png");
        Path saved = folder.resolve("subidas").resolve(url.substring("/uploads/".length()));
        assertThat(Files.readAllBytes(saved)).containsExactly(1, 2, 3);
    }

    @Test
    void acceptsEveryAllowedExtensionInAnyCase() throws IOException {
        for (String name : new String[]{"a.jpg", "b.JPEG", "c.Png", "d.gif", "e.webp"}) {
            assertThat(controller.upload(adminToken, file(name, new byte[]{1})).getStatusCode().value()).as(name).isEqualTo(200);
        }
        assertThat(storedFiles()).isEqualTo(5);
    }

    @Test
    void aPathInTheOriginalNameCannotMakeTheFileLandOutsideTheUploadsFolder() throws IOException {
        ResponseEntity<?> response = controller.upload(adminToken, file("../../escapada.png", new byte[]{1}));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(storedFiles()).isEqualTo(1);
        try (Stream<Path> all = Files.walk(folder)) {
            assertThat(all.filter(Files::isRegularFile).map(p -> p.getFileName().toString()))
                    .noneMatch(n -> n.contains("escapada"));
        }
        assertThat(Files.exists(folder.getParent().resolve("escapada.png"))).isFalse();
    }

    @Test
    void whenTheFolderCannotBeCreatedItAnswersWithAServerError() throws IOException {
        Path notAFolder = Files.writeString(folder.resolve("es-un-archivo"), "x");
        UploadController broken = new UploadController(
                new AdminGuard(new JwtUtil("test-secret-test-secret-test-secret-test-secret", 60_000)), notAFolder.toString());

        ResponseEntity<?> response = broken.upload(adminToken, file("foto.png", new byte[]{1}));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isEqualTo(Map.of("error", "No se pudo guardar el archivo"));
    }
}
