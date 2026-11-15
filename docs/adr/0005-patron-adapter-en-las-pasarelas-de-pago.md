# ADR 0005: Patrón Adapter (puerto y adaptadores) para las pasarelas de pago

- **Estado:** Aceptada
- **Fecha:** 2026-11-15
- **Tipo:** Patrón de diseño (estructura) / integración

## Contexto
La tienda cobra con dos sistemas externos de naturaleza muy distinta: el sistema bancario **legado por SOAP** (autorizar y
consultar un pago con un `idTransaccion`) y **MercadoPago** por REST (Checkout Pro, consulta de pagos y reembolsos). El negocio
(`VentaService`) no puede depender de XML ni de HTTP ni de las particularidades de cada proveedor.

## Decisión
Aplicar **Adapter**: el negocio habla con dos interfaces propias (puertos) y cada proveedor se adapta detrás de una
implementación.

| Puerto (lo que el negocio necesita) | Adaptador | Protocolo |
| --- | --- | --- |
| `PaymentGateway` (`autorizar`, `consultar`) | `SoapPaymentGateway` | SOAP (Spring-WS); traduce el `Fault` a `GatewayFaultException` |
| `MercadoPagoClient` (preferencia, pagos, reembolso) | `HttpMercadoPagoClient` | REST |

El negocio recibe tipos propios (`AuthResult`, `PaymentStatus`) y no ve nada del proveedor. La pasarela bancaria simulada
(`PasarelaBancariaSimulada`, `PagosEndpoint`) hace de sistema legado en el mismo proceso, y el cliente SOAP la llama igual
que llamaría a la real.

## Alternativas descartadas
- **Llamar al SOAP/REST directo desde `VentaService`:** acopla el negocio al protocolo y obliga a levantar el proveedor para
  probar una regla.
- **Una sola interfaz para ambos proveedores:** sus flujos no coinciden (uno autoriza en línea, el otro redirige al cliente a
  pagar y confirma después).

## Consecuencias
- (+) `VentaServiceTest` usa dobles de `PaymentGateway` y `MercadoPagoClient`: pruebas rápidas de aprobado, rechazado, timeout
  y datos inválidos sin red.
- (+) Cambiar o agregar un proveedor es una clase nueva.
- (−) Hay que mantener la traducción de errores en cada adaptador (por ejemplo, `Fault` → rechazo o datos inválidos).

## Implementación y evidencia
- `payment/PaymentGateway.java`, `payment/soap/SoapPaymentGateway.java`, `payment/soap/GatewayFaultResolver.java`.
- `payment/mercadopago/MercadoPagoClient.java`, `HttpMercadoPagoClient.java`.
- `config/SoapConfig` (timeouts: conexión 3 s, lectura 5 s) y `config/PaymentConfig`.
- Pruebas: `VentaServiceTest`, pruebas SOAP del endpoint y del cliente, y la sonda de seguridad con XXE contra el canal SOAP.
