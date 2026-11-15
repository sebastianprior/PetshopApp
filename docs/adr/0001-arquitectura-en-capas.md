# ADR 0001: Arquitectura en capas

- **Estado:** Aceptada
- **Fecha:** 2026-11-15
- **Tipo:** Arquitectura

## Contexto
La tienda tiene reglas de negocio no triviales (stock, cupones, envío por código postal, pagos, devoluciones) que tienen que
poder probarse sin levantar el servidor ni la base de datos, y un front que consume la API por HTTP.

## Decisión
Backend Spring Boot en capas con dependencias en una sola dirección:
`controller` (HTTP, validación de entrada y de sesión) → `service` (reglas de negocio y orquestación) → `repository` (acceso a
datos con Spring Data JPA) → `model` (entidades). Los `dto` son el contrato público y las entidades no salen del backend.
Las integraciones con terceros viven aparte (`payment`, `config`) detrás de interfaces (ver ADR 0005).

## Alternativas descartadas
- **Lógica en los controladores:** más rápido al principio, pero imposible de probar con tests unitarios rápidos.
- **Hexagonal completa / microservicios:** exceso para el tamaño del equipo y del plazo; el broker y la pasarela ya son
  fronteras externas claras.

## Consecuencias
- (+) La capa de negocio (`service`, `payment`) se prueba con repositorios simulados y está sujeta a un control de cobertura
  (JaCoCo: ≥ 80 % de líneas y ≥ 70 % de ramas) y a pruebas de mutación (PIT).
- (+) Cambiar la pasarela o la base no toca los controladores.
- (−) Más clases y DTO que mantener.

## Implementación y evidencia
- Paquetes `com.petshop.app.{controller,service,repository,model,dto,payment,config}`.
- `VentaService` orquesta el checkout; `VentaController` solo traduce HTTP.
- Pruebas unitarias de servicio (`VentaServiceTest`, 67 casos) y de controlador, y pruebas de repositorio contra MySQL real
  (`RepositoriosMySqlTest`, Testcontainers).
