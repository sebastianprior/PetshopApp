# ADR 0004: Patrón Strategy para ordenar productos

- **Estado:** Aceptada
- **Fecha:** 2026-11-15
- **Tipo:** Patrón de diseño (comportamiento)

## Contexto
El catálogo se lista ordenado por precio ascendente, descendente o sin orden (`GET /api/products?sort=...`). Un `if` por
criterio dentro del controlador obliga a tocarlo cada vez que aparece un criterio nuevo (por ejemplo, por valoración).

## Decisión
Aplicar **Strategy**: la interfaz `ProductSortStrategy` (`sort(List<Product>)`) define el algoritmo y cada criterio es una
implementación intercambiable (`PriceAscStrategy`, `PriceDescStrategy`). `ProductController.resolveStrategy` elige la
estrategia según el parámetro `sort`; si no coincide con ninguna, devuelve el listado sin reordenar.

## Alternativas descartadas
- **`Comparator` suelto en el controlador:** funciona, pero deja el criterio mezclado con la capa HTTP y sin un lugar propio
  donde probarlo.
- **Ordenar en la base (`ORDER BY` dinámico):** no justifica una consulta distinta por criterio para un catálogo de 50 productos.

## Consecuencias
- (+) Un criterio nuevo es una clase nueva más una línea en `resolveStrategy`, sin modificar los existentes (abierto/cerrado).
- (+) Cada estrategia se prueba aislada.
- (−) Dos clases para lo que sería una línea con `Comparator`; se acepta por la claridad del punto de extensión.

## Implementación y evidencia
- `service/ProductSortStrategy.java`, `service/PriceAscStrategy.java`, `service/PriceDescStrategy.java`.
- `controller/ProductController.java` (`resolveStrategy`).
- Pruebas: `ProductControllerTest` (orden ascendente, descendente y sin criterio).
