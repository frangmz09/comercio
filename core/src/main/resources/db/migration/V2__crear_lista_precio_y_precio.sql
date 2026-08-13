-- Habilita combinar '=' sobre columnas escalares con '&&' sobre rangos en un mismo
-- indice GiST, que es lo que necesita la constraint de exclusion de mas abajo.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE lista_precio (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    codigo    VARCHAR(30)  NOT NULL,
    nombre    VARCHAR(100) NOT NULL,
    activa    BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version   BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT uk_lista_precio_codigo UNIQUE (codigo)
);

CREATE TABLE precio (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    producto_id     UUID           NOT NULL REFERENCES producto (id),
    lista_precio_id UUID           NOT NULL REFERENCES lista_precio (id),
    monto           NUMERIC(14, 2) NOT NULL,
    vigencia_desde  TIMESTAMPTZ    NOT NULL,
    -- NULL = vigente hasta que llegue un precio nuevo que lo cierre.
    vigencia_hasta  TIMESTAMPTZ,
    creado_en       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version         BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT ck_precio_monto_positivo CHECK (monto > 0),
    CONSTRAINT ck_precio_vigencia_coherente CHECK (vigencia_hasta IS NULL OR vigencia_hasta > vigencia_desde)
);

-- La garantia real de que un producto no tenga dos precios simultaneos en la misma
-- lista. El servicio cierra la vigencia anterior al insertar una nueva, pero eso solo
-- vale si nadie escribe en paralelo: bajo concurrencia, dos transacciones pueden leer
-- el mismo estado y ambas insertar. Esta constraint hace que la segunda falle en la
-- base, que es el unico lugar donde la exclusion se puede garantizar de verdad.
-- Los rangos son [desde, hasta), asi que cerrar uno en el instante en que abre el
-- siguiente no cuenta como solapamiento.
ALTER TABLE precio
    ADD CONSTRAINT ex_precio_sin_solapamiento
        EXCLUDE USING gist (
            producto_id WITH =,
            lista_precio_id WITH =,
            tstzrange(vigencia_desde, vigencia_hasta) WITH &&
        );

CREATE INDEX idx_precio_lookup ON precio (producto_id, lista_precio_id, vigencia_desde DESC);
