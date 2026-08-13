# Changelog

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/), y este
proyecto sigue [Versionado Semántico](https://semver.org/lang/es/).

## [No publicado]

### Añadido
- Autenticación con JWT y roles `ADMIN` / `VENDEDOR`.

## [0.1.0] — 2026-08-13

Primera versión funcional: el core transaccional completo, con el servicio de reportes
consumiendo eventos y la API desplegada públicamente.

### Añadido

**Catálogo y precios**
- Alta, consulta, actualización y baja lógica de productos, con listado paginado y
  filtro por categoría.
- Listas de precios con vigencia temporal `[desde, hasta)`. Cargar un precio cierra el
  anterior en lugar de pisarlo, y el historial completo queda consultable.
- Constraint `EXCLUDE USING gist` que impide a nivel de base que un producto tenga dos
  precios simultáneos en la misma lista.

**Stock**
- Libro de movimientos append-only (`ENTRADA`, `SALIDA`, `AJUSTE`) con saldo
  materializado por producto.
- Bloqueo pesimista sobre el saldo y `CHECK (cantidad >= 0)`: el stock no queda negativo
  ni bajo concurrencia.

**Ventas y comprobantes**
- Venta atómica que resuelve el precio vigente, descuenta el stock, calcula el total y
  emite la factura en una sola transacción. Cada línea congela su precio unitario.
- Idempotencia por header `Idempotency-Key`, respaldada por la clave primaria de
  `clave_idempotencia`.
- Numeración de comprobantes por punto de venta, sin huecos ni repetidos.
- Nota de crédito que revierte la venta, devuelve la mercadería y apunta a la factura
  original sin modificarla.

**Eventos y reportes**
- Outbox transaccional: el evento se escribe en la misma transacción que la venta y un
  publisher aparte lo envía a Kafka.
- Servicio `reportes` con su propia base, consumo idempotente y dead letter topic.
- Proyecciones de ventas por día y productos más vendidos.

**Infraestructura**
- Monorepo Maven multi-módulo, migraciones con Flyway y `ddl-auto: validate`.
- 38 tests, incluidos los de concurrencia con PostgreSQL y Kafka reales vía
  Testcontainers.
- CI en GitHub Actions con cobertura JaCoCo, más un `Jenkinsfile` equivalente.
- Observabilidad con Actuator, Prometheus y un dashboard de Grafana aprovisionado.
- Despliegue público en Render con blueprint versionado.

[No publicado]: https://github.com/frangmz09/comercio/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/frangmz09/comercio/releases/tag/v0.1.0
