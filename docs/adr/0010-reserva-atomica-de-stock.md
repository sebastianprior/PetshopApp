# ADR 0010: Reserva atómica de stock en la base de datos

- **Estado:** Aceptada
- **Fecha:** 2026-11-15
- **Tipo:** Concurrencia / consistencia de datos

## Contexto
El stock se comprobaba en un bucle y se descontaba leyendo y reescribiendo el valor: con compradores simultáneos hay una
condición de carrera (**sobreventa**). La prueba `concurrencia_stock.py` lo mostró: con stock 1 y 12 compradores a la vez,
10 compras salieron bien (defecto crítico D-01).

## Decisión
Reservar el stock con una sola sentencia atómica por producto o variante:
`UPDATE ... SET stock = stock - :cantidad WHERE id = :id AND stock >= :cantidad`. Si afecta 0 filas, no había stock. La reserva
del carrito es **todo o nada**: si falta una línea, se libera lo reservado y la compra se rechaza. Cancelar devuelve el stock
con `UPDATE ... SET stock = stock + :cantidad`.

## Alternativas descartadas
- **Bloqueo pesimista (`SELECT ... FOR UPDATE`) por producto:** correcto, pero serializa más de lo necesario.
- **`@Version` con reintento:** funciona, pero con mucha contención genera ráfagas de reintentos.
- **Mutex en memoria del backend:** no sirve con más de una instancia.

## Consecuencias
- (+) El motor de la base es el árbitro: no puede venderse más de lo que hay, con una o varias instancias.
- (+) Sin transacciones largas ni bloqueos manuales.
- (−) Las pruebas con repositorios simulados no ven carreras: hace falta MySQL real (por eso `RepositoriosMySqlTest`).

## Implementación y evidencia
- `repository/ProductRepository.reservar/liberar` y `ProductVariantRepository.reservar/liberar`; `VentaService.reservarStock`.
- Pruebas: 30 hilos disputando 1 unidad en MySQL real (`RepositoriosMySqlTest`, Testcontainers) y `concurrencia_stock.py`
  contra el sistema levantado en el CI (exactamente 1 compra exitosa).
