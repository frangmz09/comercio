# Changelog

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/), y este
proyecto sigue [Versionado Semántico](https://semver.org/lang/es/).

## [No publicado]

### Agregado
- El documento OpenAPI declara el contrato de errores completo: `400`, `401`, `403`,
  `404`, `409` y `500`, cada uno con el `ApiError` que la API devuelve. Antes las 23
  operaciones anunciaban un único `200`, lo que dejaba invisible en Swagger justamente
  la parte más trabajada del proyecto —los `409` de concurrencia y de stock—.
- Todos los campos de los cuerpos de petición traen ejemplos válidos. Sin ellos Swagger
  UI rellenaba los `UUID` con el literal `"string"` y cualquier escritura probada desde
  «Try it out» fallaba con un `400`.
- `ContratoOpenApiIT` verifica contra la aplicación levantada que lo documentado siga
  coincidiendo con lo que la API hace: que cada operación declare su código de éxito real
  y uno solo, que las escrituras declaren `401`, que las consultas no figuren como
  protegidas, y que todo cuerpo de petición tenga ejemplos.

### Corregido
- El `401` no distinguía entre token ausente, vencido y mal formado. Los tres se arreglan
  de manera distinta —volver a loguearse, revisar qué se pegó en `Authorize`— y un
  mensaje único obligaba a adivinar. `JwtService.verificar` ahora devuelve un resultado
  tipado y el mensaje sale de ahí.
- El `400` por un valor que no entra en el tipo del campo respondía «el cuerpo de la
  petición no es JSON válido», cuando el JSON era válido y el problema era un `UUID` o un
  enum mal escrito. Ahora nombra el campo, el valor recibido y lo que se esperaba; para
  un enum, enumera los valores admitidos.
- Las consultas figuraban en Swagger con el candado de autenticación aunque `GET` sea
  público, y el `login` también, pese a ser el endpoint que emite el token.
- `MovimientoRequest` publicaba `cantidadDistintaDeCero` como si fuera un campo del
  cuerpo. Es el getter de la validación `@AssertTrue`, que springdoc tomaba por un dato.
- `AutenticacionIT` daba por probado el vencimiento del token pero firmaba con un secreto
  distinto al de la aplicación: lo que fallaba era la verificación de la firma y el token
  nunca llegaba a evaluarse por vigencia. El caso quedaba sin cubrir.

- Las carreras de concurrencia que la base rechaza devolvían `500` en lugar de `409`.
  Dos altas simultáneas del mismo SKU, o dos notas de crédito sobre la misma venta,
  terminaban en «error interno» cuando lo correcto es decirle al cliente que reintente.
  Se distingue por `SQLState`: unicidad, exclusión y `CHECK` son `409`; un `NOT NULL` sin
  valor sigue siendo `500`, porque eso sí es un defecto del código.
- La portada de Swagger anunciaba que los precios, el stock, las ventas y los
  comprobantes «se suman en las próximas iteraciones», cuando los cuatro ya estaban
  operativos desde la `0.1.0`.
- Los ejemplos de `curl` del README no mandaban el token. Desde la `0.2.0` las escrituras
  lo exigen, así que quien los copiaba recibía un `401` en lugar del comportamiento que
  el propio ejemplo documentaba.
- El pipeline de Jenkins marcaba el build como fallido si el análisis de SonarCloud no
  corría, en contra de la política que el proyecto declara y que el workflow de Actions
  sí respeta. Ahora queda `UNSTABLE`: visible, pero sin teñir de rojo un build cuyos
  tests pasaron.
- Los reportes de ventas agrupan por fecha UTC, pero el rango por defecto se calculaba
  en la zona del servidor. En Argentina (UTC-3), entre las 21 y la medianoche el reporte
  devolvía el día anterior y las ventas del día quedaban fuera del rango.
- La cobertura no llegaba a SonarCloud: el valor de `sonar.coverage.jacoco.xmlReportPaths`
  estaba escrito en varias líneas y Maven no recorta el valor de una propiedad.
- Ordenar un listado por un campo inexistente devolvía `500` en lugar de `400`. Swagger UI
  lo provocaba con solo apretar Execute, porque presentaba la paginación como un objeto
  JSON obligatorio con `sort: ["string"]`. Ahora el `400` nombra el campo, y `page`,
  `size` y `sort` figuran en Swagger como parámetros sueltos y opcionales.

### Cambiado
- El fallo del análisis de SonarCloud deja un aviso en el resumen del run. Seguía sin
  frenar el merge, pero `continue-on-error` lo dejaba mudo: el paso figuraba en verde
  aunque el comando hubiera fallado.
- La plantilla de pull request pide tachar los ítems que no vienen al caso en lugar de
  marcarlos: un `[x]` que aclara «no aplica» se lee como que sí se hizo.
- El README enlaza `CONTRIBUTING.md` y `CHANGELOG.md`, que hasta ahora no eran
  alcanzables desde la puerta de entrada del repositorio.
- Los roles en las reglas de autorización salen del enum `Rol` y no de literales
  repetidos, para que renombrar uno rompa la compilación en lugar de dejar una regla que
  no coincide con nada.
- Se silencian dos familias de falsos positivos de Sonar, con el motivo documentado:
  reglas de Oracle aplicadas a DDL de PostgreSQL, y detección de `TODO` disparada por la
  palabra española «todo» en los comentarios.

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
