CREATE TABLE punto_venta (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    numero    INTEGER      NOT NULL,
    nombre    VARCHAR(100) NOT NULL,
    activo    BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uk_punto_venta_numero UNIQUE (numero),
    CONSTRAINT ck_punto_venta_numero_positivo CHECK (numero > 0)
);

-- Una fila por (punto de venta, tipo) con el proximo numero a emitir. Es deliberadamente
-- una tabla y no una SEQUENCE de Postgres: las secuencias no son transaccionales y dejan
-- huecos cuando una transaccion se revierte. Para un comprobante fiscal un hueco no es
-- un detalle estetico, es una inconsistencia que hay que justificar.
CREATE TABLE secuencia_comprobante (
    punto_venta_id UUID        NOT NULL REFERENCES punto_venta (id),
    tipo           VARCHAR(20) NOT NULL,
    proximo_numero BIGINT      NOT NULL DEFAULT 1,

    PRIMARY KEY (punto_venta_id, tipo),
    CONSTRAINT ck_secuencia_proximo_positivo CHECK (proximo_numero > 0)
);

CREATE TABLE comprobante (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    punto_venta_id UUID           NOT NULL REFERENCES punto_venta (id),
    tipo           VARCHAR(20)    NOT NULL,
    numero         BIGINT         NOT NULL,
    venta_id       UUID           NOT NULL REFERENCES venta (id),
    -- Una nota de credito apunta al comprobante que revierte. NULL en facturas y remitos.
    revierte_a     UUID           REFERENCES comprobante (id),
    total          NUMERIC(14, 2) NOT NULL,
    creado_en      TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT ck_comprobante_tipo CHECK (tipo IN ('FACTURA', 'REMITO', 'NOTA_CREDITO')),
    -- La garantia de que no se emitan dos comprobantes con el mismo numero en el mismo
    -- punto de venta. El lock sobre la secuencia serializa; esto lo hace imposible.
    CONSTRAINT uk_comprobante_numeracion UNIQUE (punto_venta_id, tipo, numero),
    -- Una venta no puede tener dos notas de credito ni dos facturas.
    CONSTRAINT uk_comprobante_por_venta UNIQUE (venta_id, tipo)
);

CREATE INDEX idx_comprobante_venta ON comprobante (venta_id);

-- Outbox transaccional. El evento se escribe aca en la MISMA transaccion que la venta;
-- un publisher aparte lo lee y lo manda a Kafka. Publicar directamente desde el servicio
-- seria un dual-write: la venta puede commitear y el publish fallar (evento perdido), o
-- el publish salir y la transaccion revertirse (evento fantasma). Con outbox, el evento
-- existe si y solo si la venta existe.
CREATE TABLE outbox (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Identidad del agregado que genero el evento, para poder particionar por el en Kafka
    -- y asi conservar el orden de los eventos de una misma venta.
    agregado_id  UUID         NOT NULL,
    tipo         VARCHAR(60)  NOT NULL,
    payload      JSONB        NOT NULL,
    creado_en    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- NULL mientras esta pendiente. Se marca en lugar de borrar para poder auditar que
    -- se publico y cuando.
    publicado_en TIMESTAMPTZ,
    intentos     INTEGER      NOT NULL DEFAULT 0,
    ultimo_error TEXT
);

-- Indice parcial: el publisher solo consulta pendientes, y una vez publicados las filas
-- dejan de pesar en el indice aunque queden en la tabla.
CREATE INDEX idx_outbox_pendientes ON outbox (creado_en) WHERE publicado_en IS NULL;
