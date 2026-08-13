CREATE TABLE venta (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lista_precio_id UUID           NOT NULL REFERENCES lista_precio (id),
    total           NUMERIC(14, 2) NOT NULL DEFAULT 0,
    estado          VARCHAR(20)    NOT NULL,
    creado_en       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version         BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT ck_venta_estado CHECK (estado IN ('CONFIRMADA', 'ANULADA')),
    CONSTRAINT ck_venta_total_no_negativo CHECK (total >= 0)
);

CREATE TABLE linea_venta (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    venta_id        UUID           NOT NULL REFERENCES venta (id) ON DELETE CASCADE,
    producto_id     UUID           NOT NULL REFERENCES producto (id),
    cantidad        NUMERIC(14, 3) NOT NULL,
    -- Congelado al momento de la venta. Es la razon por la que existe todo el modulo de
    -- vigencias: si el precio se leyera del catalogo al reimprimir, una venta de hace
    -- seis meses mostraria el precio de hoy.
    precio_unitario NUMERIC(14, 2) NOT NULL,
    subtotal        NUMERIC(14, 2) NOT NULL,

    CONSTRAINT ck_linea_cantidad_positiva CHECK (cantidad > 0),
    CONSTRAINT ck_linea_precio_positivo CHECK (precio_unitario > 0),
    -- Un producto no puede aparecer dos veces en la misma venta: se acumula en una linea.
    CONSTRAINT uk_linea_producto_por_venta UNIQUE (venta_id, producto_id)
);

CREATE INDEX idx_linea_venta ON linea_venta (venta_id);

-- Un punto de venta que no recibe respuesta reintenta, y no tiene forma de saber si la
-- venta se registro o no. La clave la genera el cliente y viaja en el header
-- Idempotency-Key: si llega dos veces, la segunda devuelve la venta original en lugar
-- de cobrar de nuevo.
CREATE TABLE clave_idempotencia (
    clave     VARCHAR(120) PRIMARY KEY,
    -- Hash del cuerpo del pedido. Si alguien reusa la clave con otro contenido es un
    -- error del cliente, no un reintento, y hay que avisarlo en vez de devolver la
    -- venta vieja como si nada.
    huella    VARCHAR(64)  NOT NULL,
    venta_id  UUID         NOT NULL REFERENCES venta (id),
    creado_en TIMESTAMPTZ  NOT NULL DEFAULT now()
);
