CREATE TABLE usuario (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username    VARCHAR(60)  NOT NULL,
    -- Hash BCrypt, nunca la contrasena. El largo alcanza para el formato completo
    -- ($2a$10$ + 53 caracteres) con margen para cambiar el factor de costo.
    password    VARCHAR(100) NOT NULL,
    rol         VARCHAR(20)  NOT NULL,
    activo      BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uk_usuario_username UNIQUE (username),
    CONSTRAINT ck_usuario_rol CHECK (rol IN ('ADMIN', 'VENDEDOR'))
);
