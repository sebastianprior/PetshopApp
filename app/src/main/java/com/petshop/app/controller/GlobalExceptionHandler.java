package com.petshop.app.controller;

import com.petshop.app.model.TransicionInvalidaException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("Datos inválidos");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", message));
    }

    /** Red de seguridad del patrón State: cualquier transición de venta que un estado no permita es un 409 (Problem Details). */
    @ExceptionHandler(TransicionInvalidaException.class)
    public ResponseEntity<?> handleTransicionInvalida(TransicionInvalidaException ex) {
        return Problems.of(409, "transicion-invalida", "Transición inválida", ex.getMessage());
    }
}
