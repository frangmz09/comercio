# comercio

[![CI](https://github.com/frangmz09/comercio/actions/workflows/ci.yml/badge.svg)](https://github.com/frangmz09/comercio/actions/workflows/ci.yml)

API REST para el core transaccional de un comercio minorista: catálogo de productos,
listas de precios con vigencia temporal y control de stock por movimientos.

Está construida alrededor de las reglas que un sistema de gestión real tiene que
sostener y que un CRUD no cubre: que el stock nunca quede negativo aunque dos cajas
vendan el mismo producto en el mismo instante, y que reimprimir una venta de hace seis
meses devuelva el precio que regía ese día y no el de hoy.

> El dominio es sintético. Es un proyecto propio, sin relación con ningún sistema de un
> empleador. Ver [Sobre los datos](#sobre-los-datos).

## Qué resuelve

**Catálogo.** Productos identificados por SKU, con categoría y unidad de medida. Los
productos no se borran: se desactivan, porque hay movimientos y ventas históricas que
los referencian.

**Precios con vigencia.** Un producto no tiene *un* precio: tiene un precio por lista
(minorista, mayorista, un convenio puntual) y cada uno rige durante una ventana de
tiempo. Cargar un precio nuevo no pisa al anterior, lo cierra. El historial completo
queda consultable y cada venta puede explicar a qué precio se vendió.

**Stock.** El saldo de un producto es la consecuencia de un libro de movimientos que
nunca se edita. Una corrección de inventario no modifica el pasado: agrega un ajuste.
Una salida que dejaría el saldo en negativo se rechaza, incluso bajo concurrencia.

## Cómo correrlo

Requiere Docker.

```bash
docker compose up --build
```

| | |
|---|---|
| API | http://localhost:8080/api/v1 |
| Swagger UI | http://localhost:8080/swagger-ui |
| Health | http://localhost:8080/actuator/health |

## La API

### Productos

| Método | Ruta | Qué hace |
|---|---|---|
| `POST` | `/api/v1/productos` | Da de alta un producto |
| `GET` | `/api/v1/productos/{id}` | Devuelve un producto |
| `GET` | `/api/v1/productos` | Lista paginada, filtrable por `categoria` e `incluirInactivos` |
| `PUT` | `/api/v1/productos/{id}` | Actualiza nombre, categoría y unidad |
| `POST` | `/api/v1/productos/{id}/desactivar` | Lo saca del catálogo activo sin borrarlo |
| `POST` | `/api/v1/productos/{id}/activar` | Lo reincorpora |

### Precios

| Método | Ruta | Qué hace |
|---|---|---|
| `POST` | `/api/v1/listas-precio` | Crea una lista (minorista, mayorista…) |
| `GET` | `/api/v1/listas-precio` | Lista paginada de listas de precios |
| `POST` | `/api/v1/listas-precio/{listaId}/precios` | Asigna un precio y cierra la vigencia del anterior |
| `GET` | `/api/v1/listas-precio/{listaId}/precios/vigente` | Precio que rige en un `momento` dado (por defecto, ahora) |
| `GET` | `/api/v1/listas-precio/{listaId}/precios/historial` | Todos los precios, del más nuevo al más viejo |

### Stock

| Método | Ruta | Qué hace |
|---|---|---|
| `POST` | `/api/v1/stock/movimientos` | Registra una `ENTRADA`, `SALIDA` o `AJUSTE` |
| `GET` | `/api/v1/stock/{productoId}` | Saldo actual |
| `GET` | `/api/v1/stock/{productoId}/movimientos` | Historial de movimientos |

Todos los errores tienen la misma forma, sin importar dónde se originaron:

```json
{
  "timestamp": "2026-08-13T01:17:32.955Z",
  "status": 409,
  "error": "Conflict",
  "message": "Stock insuficiente: hay 3 y se intentan descontar 5",
  "path": "/api/v1/stock/movimientos",
  "validationErrors": null
}
```

### Un flujo completo

```bash
# 1. Un producto
curl -X POST http://localhost:8080/api/v1/productos \
  -H "Content-Type: application/json" \
  -d '{"sku":"ALM-001","nombre":"Fideos 500g","categoria":"Almacén","unidad":"unidad"}'

# 2. Una lista de precios
curl -X POST http://localhost:8080/api/v1/listas-precio \
  -H "Content-Type: application/json" \
  -d '{"codigo":"MINORISTA","nombre":"Minorista"}'

# 3. Su precio
curl -X POST http://localhost:8080/api/v1/listas-precio/{listaId}/precios \
  -H "Content-Type: application/json" \
  -d '{"productoId":"{productoId}","monto":1250.00}'

# 4. Entra mercadería
curl -X POST http://localhost:8080/api/v1/stock/movimientos \
  -H "Content-Type: application/json" \
  -d '{"productoId":"{productoId}","tipo":"ENTRADA","cantidad":100,"motivo":"compra a proveedor"}'

# 5. El saldo quedó en 100
curl http://localhost:8080/api/v1/stock/{productoId}

# 6. Una salida mayor al saldo se rechaza con 409 — el stock nunca queda negativo
curl -X POST http://localhost:8080/api/v1/stock/movimientos \
  -H "Content-Type: application/json" \
  -d '{"productoId":"{productoId}","tipo":"SALIDA","cantidad":99999,"motivo":"venta"}'

# 7. Sube el precio: el anterior no se pisa, se cierra
curl -X POST http://localhost:8080/api/v1/listas-precio/{listaId}/precios \
  -H "Content-Type: application/json" \
  -d '{"productoId":"{productoId}","monto":1490.00}'

# 8. El precio de la semana pasada sigue siendo el viejo
curl "http://localhost:8080/api/v1/listas-precio/{listaId}/precios/vigente?productoId={productoId}&momento=2026-08-06T00:00:00Z"
```

## Cómo funciona por dentro

**El no solapamiento de precios lo garantiza la base, no el código.** Una constraint
`EXCLUDE USING gist` sobre `tstzrange(vigencia_desde, vigencia_hasta)` impide que un
producto tenga dos precios simultáneos en la misma lista. El servicio cierra la vigencia
anterior al cargar una nueva, pero eso solo alcanza si nadie escribe en paralelo: bajo
concurrencia, dos transacciones leen el mismo estado y ambas insertan. La base es el
único lugar donde la exclusión se puede garantizar de verdad.

**El saldo de stock existe para poder bloquearlo.** Se podría calcular con un `SUM()`
sobre los movimientos, pero eso obliga a recorrer todo el historial en cada venta y, más
importante, no deja una fila sobre la cual serializar. El saldo materializado permite un
`SELECT ... FOR UPDATE` que hace que dos ventas simultáneas del mismo producto se
ordenen: la segunda ve el saldo ya descontado por la primera en lugar de leer un valor
viejo y sobrevender.

**El lock se toma antes de validar.** Leer el saldo, concluir que alcanza y recién
después descontar es exactamente cómo se sobrevende. Como última línea de defensa hay un
`CHECK (cantidad >= 0)` en la tabla: aunque un bug dejara pasar una salida de más, la
base se niega a persistir un stock negativo.

**Los movimientos son inmutables.** No tienen setters ni versión: una vez escritos no se
tocan. Cualquier corrección es un `AJUSTE` nuevo, para que el historial siga explicando
cómo se llegó al saldo actual.

**Flyway es dueño del esquema.** Hibernate corre con `ddl-auto: validate`: solo verifica
que las entidades coincidan con lo que las migraciones crearon, nunca modifica la base.

### Estructura

Paquetes organizados por feature, no por capa técnica: cada módulo es una rebanada
vertical con su propio controlador, servicio, repositorio y entidades.

```
core/src/main/java/dev/francogomez/comercio/core/
├── producto/    catálogo
├── precio/      listas y vigencias
├── stock/       movimientos y saldos
└── shared/      manejo de errores y configuración
```

## Tests

```bash
./mvnw verify
```

Corre unitarios (Surefire) y de integración (Failsafe) en la misma pasada. Los de
integración levantan un PostgreSQL real con Testcontainers, así que requieren Docker.
El reporte de cobertura queda en `core/target/site/jacoco/`.

Los que importan son los de concurrencia: doce hilos frenados en una barrera común que
se sueltan a la vez para competir por el último ítem de stock. Con stock para seis,
exactamente seis salidas ganan, seis son rechazadas y el saldo termina en cero.

## Alcance

Es un core transaccional, no un ERP completo. Deliberadamente **no** incluye
facturación fiscal (AFIP/ARCA), multi-tenancy, cuentas corrientes, compras y proveedores,
caja y arqueo, ni interfaz gráfica: la superficie de uso es la API y su documentación
OpenAPI.

## Stack

Java 21 · Spring Boot 3 · Spring Data JPA · Bean Validation · PostgreSQL · Flyway ·
JUnit 5 · Mockito · Testcontainers · AssertJ · springdoc-openapi · Docker · GitHub Actions

## Sobre los datos

Todo el dominio —productos, precios, stock— es sintético y genérico de retail. Este
repositorio es un ejercicio propio, sin relación con ningún sistema de un empleador
actual o anterior.

## Licencia

[MIT](LICENSE)
