package com.petshop.app.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca una operación que solo puede usar un administrador. {@link AdminOnlyInterceptor} la controla antes de leer el
 * cuerpo del pedido: sin sesión válida responde 401 y con la sesión de un cliente, 403, sin importar qué cuerpo se envíe.
 * Cada operación marcada sigue verificando el rol por dentro (doble control).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AdminOnly {
}
