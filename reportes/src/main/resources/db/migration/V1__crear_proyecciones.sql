-- Registro de eventos ya aplicados. Kafka entrega at-least-once: el mismo evento puede
-- llegar dos veces (un rebalanceo, un reintento del publisher, un commit de offset que
-- no llego). Sin esta tabla, un duplicado sumaria la venta dos veces al reporte.
--
-- La PK es el id del evento, no un autoincremental: es la base la que rechaza el
-- reprocesamiento, no un chequeo en memoria que dos consumidores podrian pasar a la vez.
CREATE TABLE evento_procesado (
    event_id    UUID PRIMARY KEY,
    tipo        VARCHAR(60)  NOT NULL,
    procesado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Proyeccion: ventas agregadas por dia. No se guarda cada venta, se guarda lo que el
-- reporte necesita responder. Es CQRS-lite: el modelo de lectura tiene su propia forma,
-- distinta de la del modelo transaccional que la origina.
CREATE TABLE venta_diaria (
    fecha           DATE PRIMARY KEY,
    cantidad_ventas BIGINT         NOT NULL DEFAULT 0,
    total_vendido   NUMERIC(16, 2) NOT NULL DEFAULT 0,
    total_anulado   NUMERIC(16, 2) NOT NULL DEFAULT 0
);

-- Proyeccion: acumulado por producto, para responder "que se vende mas".
CREATE TABLE producto_vendido (
    sku              VARCHAR(40) PRIMARY KEY,
    unidades         NUMERIC(16, 3) NOT NULL DEFAULT 0,
    total_facturado  NUMERIC(16, 2) NOT NULL DEFAULT 0,
    ultima_venta     TIMESTAMPTZ
);
