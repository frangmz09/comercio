# Changelog

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/), y este
proyecto sigue [Versionado Semántico](https://semver.org/lang/es/).

## [No publicado]

## [0.2.0] — 2026-08-14

> [!WARNING]
> **Cambio incompatible.** Las operaciones de escritura pasan a exigir autenticación.
> Un cliente que antes hacía `POST`, `PUT` o `DELETE` sin credenciales ahora recibe
> `401`. Para seguir funcionando tiene que autenticarse contra
> `POST /api/v1/auth/login` y mandar el token en `Authorization: Bearer <token>`.
>
> Las consultas `GET` no cambian: siguen siendo públicas.
>
> El bump es `MINOR` y no `MAJOR` porque el proyecto está en `0.x`, donde SemVer no
> considera estable la API pública. Después de un `1.0.0`, este mismo cambio sería
> `2.0.0`.

### Añadido
- Autenticación con JWT: `POST /api/v1/auth/login` devuelve un token firmado con HS256,
  y las contraseñas se guardan con BCrypt.
- Roles `ADMIN` y `VENDEDOR`. El catálogo, las listas de precios y los puntos de venta
  los administra ADMIN; vender y mover stock lo pueden hacer ambos.
- Botón *Authorize* en Swagger UI: se pega el token una vez y queda aplicado a todas
  las llamadas.
- Usuarios de demostración creados en el arranque, desactivables con
  `comercio.demo.seed-usuarios=false`.

### Cambiado
- Las operaciones que modifican estado ahora requieren token. **Las consultas GET siguen
  siendo públicas**, para que la demo se pueda recorrer sin registrarse.
- Los 401 y 403 devuelven el mismo `ApiError` que el resto de la API, en lugar de la
  página HTML por defecto de Spring Security.

### Seguridad
- La API pública dejó de aceptar escrituras anónimas.
- El secreto de firma se toma de `COMERCIO_JWT_SECRET` y la aplicación no arranca si
  tiene menos de 32 caracteres: una firma débil que nadie note es peor que un fallo
  ruidoso en el arranque.

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

[No publicado]: https://github.com/frangmz09/comercio/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/frangmz09/comercio/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/frangmz09/comercio/releases/tag/v0.1.0
