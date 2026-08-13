package dev.francogomez.comercio.contratos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Evento publicado por {@code core} al confirmar una venta, vía outbox transaccional.
 * Lo consume {@code reportes} para proyectar agregados de ventas.
 *
 * <p>Serializado como JSON plano (sin Avro/Schema Registry): para dos servicios y un
 * puñado de tipos de evento, un registry es infraestructura sin problema que resolver.
 * La compatibilidad se sostiene con {@code schemaVersion} y evolución aditiva de campos.
 */
public record VentaConfirmada(
        int schemaVersion,
        UUID eventId,
        UUID ventaId,
        String puntoVenta,
        Instant confirmadaEn,
        List<Linea> lineas,
        BigDecimal total
) {
    public VentaConfirmada {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion debe ser positivo");
        }
    }

    public static final int CURRENT_SCHEMA_VERSION = 1;

    /**
     * La cantidad es decimal, no entera: el catálogo admite unidades de medida como el
     * kilo, donde vender 0,750 es lo normal.
     */
    public record Linea(String sku, BigDecimal cantidad, BigDecimal precioUnitario) {
    }
}
