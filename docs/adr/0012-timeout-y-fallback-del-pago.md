# ADR 0012: Timeout de 5 segundos con fallback a «pago pendiente»

- **Estado:** Aceptada
- **Fecha:** 2026-11-15
- **Tipo:** Resiliencia

## Contexto
La pasarela bancaria legado (SOAP) puede tardar o no responder. Una llamada sin límite deja colgado al cliente y consume hilos del
servidor (defecto D-09: la llamada vencida seguía ocupada hasta 30 s y los hilos no tenían tope).

## Decisión
- La autorización se ejecuta en un pool **acotado** (`paymentExecutor`, 32 llamadas simultáneas como máximo) y se espera como máximo
  **5 s** (`petshop.payments.timeout-ms`). Pasado el plazo se **cancela** la tarea (`cancel(true)`); el cliente SOAP también tiene
  lectura de 5 s y conexión de 3 s.
- **Fallback:** si la pasarela no responde a tiempo, o está saturada, la venta no falla: queda en `PAGO_PENDIENTE` con el stock
  reservado y el cliente recibe **202**. `PendingPaymentWorker` consulta o reintenta con el **mismo `idTransaccion`** (para no
  cobrar dos veces) hasta un tope de intentos; agotado el tope, cancela la venta (estado `CANCELADA`, ver ADR 0003) y publica el
  evento de pago fallido.
- El cliente puede consultar el estado (`GET /api/v1/ventas/{id}/pago`) mientras tanto.

## Alternativas descartadas
- **Esperar sin límite:** bloquea al cliente y al servidor ante un legado lento.
- **Fallar la compra al primer timeout:** el pago pudo haberse aprobado igual; se perdería una venta cobrada.
- **Reintentar de inmediato y varias veces en la misma petición:** alarga la espera y puede duplicar el cobro.

## Consecuencias
- (+) El cliente nunca espera más de ~5 s y no se cobra dos veces.
- (+) Un legado caído degrada el servicio a «pago pendiente» en lugar de tirarlo.
- (−) El estado `PAGO_PENDIENTE` existe y hay que explicarlo al cliente (la tienda muestra la pantalla de pago pendiente).

## Implementación y evidencia
- `VentaService.autorizar/procesarPendiente`, `service/PendingPaymentWorker.java`, `config/SoapConfig`, `config/PaymentConfig`.
- Pruebas: `VentaServiceTest` (timeout → 202 + `PAGO_PENDIENTE`, pasarela saturada, tope de intentos, mismo `idTransaccion`),
  `ScheduledJobsTest`.
