package dev.francogomez.comercio.core.producto;

import java.time.Instant;
import java.util.UUID;

public record ProductoResponse(
        UUID id,
        String sku,
        String nombre,
        String categoria,
        String unidad,
        boolean activo,
        Instant creadoEn
) {
    public static ProductoResponse from(Producto p) {
        return new ProductoResponse(
                p.getId(), p.getSku(), p.getNombre(), p.getCategoria(),
                p.getUnidad(), p.isActivo(), p.getCreadoEn());
    }
}
