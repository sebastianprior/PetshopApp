package com.petshop.app.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.net.URI;

/** Respuestas de error de la API v1 según RFC 9457 (application/problem+json). */
public final class Problems {

    private Problems() {}

    public static ResponseEntity<ProblemDetail> of(int status, String slug, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status), detail);
        problem.setType(URI.create("urn:petshop:problem:" + slug));
        problem.setTitle(title);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    public static ResponseEntity<ProblemDetail> unauthorized() {
        return of(401, "no-autorizado", "No autorizado", "Falta un token válido en X-Auth-Token.");
    }

    public static ResponseEntity<ProblemDetail> forbidden(String detail) {
        return of(403, "prohibido", "Prohibido", detail);
    }

    public static ResponseEntity<ProblemDetail> notFound(String detail) {
        return of(404, "no-encontrado", "No encontrado", detail);
    }
}
