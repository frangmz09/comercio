# comercio

API REST para el core transaccional de un comercio minorista: catálogo con listas de
precios vigentes, stock por movimientos, ventas atómicas y comprobantes con numeración
sin huecos. En construcción como pieza de portfolio backend en Java/Spring Boot.

> Dominio 100% sintético — no hay lógica propietaria, esquemas ni datos de ningún
> empleador. Ver [Sobre los datos](#sobre-los-datos).

## Estado actual

Lo que hay hoy es el primer hito publicable: catálogo de productos funcionando de punta
a punta, con tests, CI y contenedor. El resto del dominio (precios, stock, ventas,
comprobantes, eventos) se construye sobre esta base — ver [Roadmap](#roadmap).

- [x] Monorepo Maven multi-módulo (`contratos` + `core`)
- [x] `core`: catálogo de productos (CRUD + listado paginado con filtro por categoría)
- [x] Manejo de errores consistente (`@RestControllerAdvice` → una sola forma de `ApiError`)
- [x] Migraciones versionadas con Flyway
- [x] Tests unitarios (Mockito) y de integración con Postgres real (Testcontainers)
- [x] Documentación interactiva con Swagger/OpenAPI
- [x] Health checks y métricas vía Actuator
- [x] Dockerfile multi-stage + `docker-compose.yml`
- [x] CI en GitHub Actions (build, tests, cobertura)
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
```

### Tests

```bash
./mvnw verify
```

Corre unitarios (Surefire) e integración con Testcontainers (Failsafe, requiere Docker)
en la misma pasada, con reporte de cobertura JaCoCo en `core/target/site/jacoco/`.

## Decisiones de diseño

- **Producto sin campo `precio`.** El precio va a vivir en `ListaPrecio` con vigencia
  (desde/hasta) — un producto puede tener varios precios simultáneos según la lista, y
  reimprimir una venta vieja tiene que devolver el precio de ese día, no el de hoy.
- **Baja lógica, no física.** `desactivar` saca el producto del catálogo activo pero no
  borra la fila: hay movimientos y ventas históricas que lo referencian.
- **`@Version` para lock optimista.** Anticipa la escritura concurrente sobre saldos de
  stock y secuencias de comprobantes que llegan en las próximas semanas.

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
