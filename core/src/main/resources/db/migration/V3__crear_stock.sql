-- Libro de movimientos: append-only. Nunca se actualiza ni se borra una fila de acá.
-- Cualquier correccion es un movimiento nuevo de tipo AJUSTE, para que el historial
-- siga explicando como se llego al saldo actual.
CREATE TABLE movimiento_stock (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    producto_id UUID           NOT NULL REFERENCES producto (id),
    tipo        VARCHAR(20)    NOT NULL,
    -- Con signo: positiva en ENTRADA, negativa en SALIDA. Guardar el signo evita tener
    -- que interpretar el tipo para sumar, y hace que el saldo sea literalmente SUM().
    cantidad    NUMERIC(14, 3) NOT NULL,
    motivo      VARCHAR(200)   NOT NULL,
    -- Apunta a lo que origino el movimiento (una venta, una nota de credito). Nullable
    -- porque una carga inicial o un ajuste manual no tienen comprobante detras.
    referencia  VARCHAR(100),
    creado_en   TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT ck_movimiento_tipo CHECK (tipo IN ('ENTRADA', 'SALIDA', 'AJUSTE')),
    CONSTRAINT ck_movimiento_cantidad_no_cero CHECK (cantidad <> 0),
    CONSTRAINT ck_movimiento_signo_coherente CHECK (
        (tipo = 'ENTRADA' AND cantidad > 0) OR
        (tipo = 'SALIDA' AND cantidad < 0) OR
        (tipo = 'AJUSTE')
    )
);

CREATE INDEX idx_movimiento_producto ON movimiento_stock (producto_id, creado_en DESC);

-- Saldo materializado. Se podria calcular con SUM() sobre los movimientos, pero eso
-- obliga a recorrer todo el historial en cada venta y no da una fila sobre la cual
-- serializar la concurrencia. Tener la fila es lo que permite el SELECT FOR UPDATE.
CREATE TABLE saldo_stock (
    producto_id UUID PRIMARY KEY REFERENCES producto (id),
    cantidad    NUMERIC(14, 3) NOT NULL DEFAULT 0,
    version     BIGINT         NOT NULL DEFAULT 0,

    -- La ultima linea de defensa: aunque un bug en el servicio deje pasar una salida
    -- de mas, la base se niega a persistir un stock negativo.
    CONSTRAINT ck_saldo_no_negativo CHECK (cantidad >= 0)
);
