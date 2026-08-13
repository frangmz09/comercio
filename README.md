# comercio

API REST para el core transaccional de un comercio minorista: catálogo con listas de
precios vigentes, stock por movimientos, ventas atómicas y comprobantes con numeración
sin huecos. En construcción como pieza de portfolio backend en Java/Spring Boot.

> Dominio 100% sintético — no hay lógica propietaria, esquemas ni datos de ningún
> empleador. Ver [Sobre los datos](#sobre-los-datos).

## Estado actual

Catálogo, precios con vigencia y stock transaccional funcionando de punta a punta, con
tests, CI y contenedor. Ventas, comprobantes y eventos se construyen sobre esta base —
ver [Roadmap](#roadmap).

- [x] Monorepo Maven multi-módulo (`contratos` + `core`)
- [x] Catálogo de productos (CRUD + listado paginado con filtro por categoría)
- [x] Listas de precios con **vigencia temporal** y no solapamiento garantizado por la base
- [x] Stock por **movimientos append-only** con saldo materializado y bloqueo pesimista
- [x] Manejo de errores consistente (`@RestControllerAdvice` → una sola forma de `ApiError`)
- [x] Migraciones versionadas con Flyway
- [x] Tests unitarios (Mockito) y de integración con Postgres real (Testcontainers)
- [x] **Tests de concurrencia** con hilos compitiendo por el último ítem de stock
- [x] Documentación interactiva con Swagger/OpenAPI
- [x] Health checks y métricas vía Actuator
- [x] Dockerfile multi-stage + `docker-compose.yml`
- [x] CI en GitHub Actions (build, tests, cobertura)
- [ ] Ventas atómicas con idempotencia
- [ ] Comprobantes con numeración sin huecos
- [ ] Deploy público en Render
- [ ] SonarCloud (quality gate + badge)

## Stack

Java 21 · Spring Boot 3 · Spring Data JPA · Bean Validation · PostgreSQL · Flyway ·
JUnit 5 · Mockito · Testcontainers · AssertJ · springdoc-openapi · Docker · GitHub Actions.

Maven (no Gradle), paquetes organizados **por feature** (`producto/`, `shared/`), no por
capa. DTOs como `record`, sin Lombok ni MapStruct salvo que se justifiquen.

## Cómo correrlo

Requiere Docker.

```bash
docker compose up --build
```

- API: http://localhost:8080/api/v1/productos
- Swagger UI: http://localhost:8080/swagger-ui
- Health: http://localhost:8080/actuator/health

### Flujo de ejemplo por curl

```bash
# Crear un producto
curl -X POST http://localhost:8080/api/v1/productos \
  -H "Content-Type: application/json" \
  -d '{"sku":"ALM-001","nombre":"Fideos 500g","categoria":"Almacén","unidad":"unidad"}'

# Listar por categoría
curl "http://localhost:8080/api/v1/productos?categoria=Almacén"

# Desactivar (no se borra: sale del catálogo activo, la data queda)
curl -X POST http://localhost:8080/api/v1/productos/{id}/desactivar

# Crear una lista de precios y asignar un precio
curl -X POST http://localhost:8080/api/v1/listas-precio \
  -H "Content-Type: application/json" \
  -d '{"codigo":"MINORISTA","nombre":"Minorista"}'

curl -X POST http://localhost:8080/api/v1/listas-precio/{listaId}/precios \
  -H "Content-Type: application/json" \
  -d '{"productoId":"{productoId}","monto":1250.00}'

# Precio vigente hoy, y el que regía en una fecha pasada
curl "http://localhost:8080/api/v1/listas-precio/{listaId}/precios/vigente?productoId={productoId}"
curl "http://localhost:8080/api/v1/listas-precio/{listaId}/precios/vigente?productoId={productoId}&momento=2026-01-15T00:00:00Z"

# Cargar stock y consultar el saldo
curl -X POST http://localhost:8080/api/v1/stock/movimientos \
  -H "Content-Type: application/json" \
  -d '{"productoId":"{productoId}","tipo":"ENTRADA","cantidad":100,"motivo":"compra a proveedor"}'

curl http://localhost:8080/api/v1/stock/{productoId}

# Una salida mayor al saldo devuelve 409, nunca deja el stock negativo
curl -X POST http://localhost:8080/api/v1/stock/movimientos \
  -H "Content-Type: application/json" \
  -d '{"productoId":"{productoId}","tipo":"SALIDA","cantidad":99999,"motivo":"venta"}'
```

### Tests

```bash
./mvnw verify
```

Corre unitarios (Surefire) e integración con Testcontainers (Failsafe, requiere Docker)
en la misma pasada, con reporte de cobertura JaCoCo en `core/target/site/jacoco/`.

## Decisiones de diseño

- **Producto sin campo `precio`.** El precio vive en `Precio`, acotado a una ventana
  `[desde, hasta)` por lista. Un producto tiene precios distintos según a quién se le
  venda, y reimprimir una venta vieja devuelve el precio de ese día, no el de hoy.
- **El no solapamiento de precios lo garantiza Postgres, no el servicio.** Una
  constraint `EXCLUDE USING gist` sobre `tstzrange(vigencia_desde, vigencia_hasta)`
  impide que un producto tenga dos precios simultáneos en la misma lista. El servicio
  cierra la vigencia anterior al cargar una nueva, pero eso solo alcanza si nadie
  escribe en paralelo: bajo concurrencia dos transacciones leen el mismo estado y ambas
  insertan. La base es el único lugar donde la exclusión se puede garantizar de verdad.
- **Stock como libro append-only.** Los movimientos no se editan ni se borran; una
  corrección es un `AJUSTE` nuevo, para que el historial siga explicando cómo se llegó
  al saldo actual. El saldo materializado existe para no recorrer todo el historial en
  cada venta y, sobre todo, para dar una fila concreta sobre la cual serializar.
- **`SELECT ... FOR UPDATE` sobre el saldo, no validación optimista.** Se toma el lock
  *antes* de validar. Leer el saldo, decidir que alcanza y recién después descontar es
  exactamente cómo se sobrevende. Además hay un `CHECK (cantidad >= 0)` en la tabla como
  última línea de defensa ante un bug del servicio.
- **Baja lógica, no física.** `desactivar` saca el producto del catálogo activo pero no
  borra la fila: hay movimientos y ventas históricas que lo referencian.

### Una trampa que vale la pena documentar

Hibernate no ejecuta el SQL en el orden en que está escrito el código: mantiene una cola
de acciones y la ordena por tipo, con los `INSERT` antes que los `UPDATE`. Al cargar un
precio nuevo, eso hacía que el `INSERT` saliera mientras el precio anterior todavía tenía
`vigencia_hasta` en `NULL` — los rangos se solapaban y la constraint rechazaba la
operación entera. La solución es un `flush()` explícito después de cerrar la vigencia
anterior, para forzar el `UPDATE` primero. Está comentado en `PrecioService`.

La alternativa era declarar la constraint `DEFERRABLE INITIALLY DEFERRED` y dejar que
Postgres la validara en el commit. Se descartó porque entonces el error aparece al cerrar
la transacción, fuera del método del servicio, donde ya no se puede traducir a un 409 con
un mensaje útil.

## Roadmap

Ventas, stock, comprobantes, un segundo servicio de reportes consumiendo eventos de
Kafka con outbox transaccional, observabilidad con Prometheus/Grafana, y deploy en
Render. Detalle completo en las notas de diseño del proyecto.

**Fuera de alcance, por decisión:** Kubernetes/OpenShift, integración fiscal AFIP/ARCA,
multi-tenant, event sourcing completo, saga con compensación distribuida, service
discovery, API gateway, frontend.

## Sobre los datos

Todo el dominio (productos, precios, stock) es sintético y genérico de retail. Este
repositorio es un ejercicio propio de portfolio, sin relación con ningún sistema de un
empleador actual o anterior.
