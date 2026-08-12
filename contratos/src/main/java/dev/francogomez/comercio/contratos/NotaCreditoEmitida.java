package dev.francogomez.comercio.contratos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Evento publicado por {@code core} al emitir una nota de crédito que revierte una venta. */
public record NotaCreditoEmitida(
        int schemaVersion,
        UUID eventId,
        UUID notaCreditoId,
        UUID ventaOriginalId,
        Instant emitidaEn,
        BigDecimal total
) {
    public NotaCreditoEmitida {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion debe ser positivo");
        }
    }
}
