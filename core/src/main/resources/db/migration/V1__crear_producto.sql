CREATE TABLE producto (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sku        VARCHAR(40)  NOT NULL,
    nombre     VARCHAR(200) NOT NULL,
    categoria  VARCHAR(100),
    unidad     VARCHAR(20)  NOT NULL,
    activo     BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version    BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT uk_producto_sku UNIQUE (sku)
);

CREATE INDEX idx_producto_categoria ON producto (categoria);
