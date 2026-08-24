package dev.francogomez.comercio.core.precio;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTOs del feature de precios. Van juntos en un archivo porque son records chicos que
 * solo tienen sentido leídos como conjunto.
 */
public final class PrecioDtos {

    private PrecioDtos() {
    }

    public record ListaPrecioRequest(
            @NotBlank @Size(max = 30)
            @Schema(description = "Código corto de la lista, único", example = "MINORISTA")
            String codigo,

            @NotBlank @Size(max = 100)
            @Schema(example = "Lista minorista")
            String nombre) {
    }

    public record ListaPrecioResponse(
            UUID id,
            String codigo,
            String nombre,
            boolean activa,
            Instant creadoEn) {

        public static ListaPrecioResponse from(ListaPrecio l) {
            return new ListaPrecioResponse(l.getId(), l.getCodigo(), l.getNombre(), l.isActiva(), l.getCreadoEn());
        }
    }

    public record PrecioRequest(
            @NotNull
            @Schema(description = "Id que devolvió POST /api/v1/productos",
                    example = "9b1f2c3d-4e5a-4b6c-8d7e-0f1a2b3c4d5e")
            UUID productoId,

            @NotNull
            @DecimalMin(value = "0.01", message = "el monto debe ser mayor a cero")
            @Digits(integer = 12, fraction = 2)
            @Schema(description = "Precio unitario, con hasta dos decimales", example = "1250.50")
            BigDecimal monto,

            /** Opcional: si no viene, el precio rige desde el momento del alta. */
            @Schema(description = "Desde cuándo rige. Si se omite, rige desde ahora.",
                    example = "2026-09-01T00:00:00Z")
            Instant vigenciaDesde) {
    }

    public record PrecioResponse(
            UUID id,
            UUID productoId,
            UUID listaPrecioId,
            BigDecimal monto,
            Instant vigenciaDesde,
            Instant vigenciaHasta,
            boolean abierto) {

        public static PrecioResponse from(Precio p) {
            return new PrecioResponse(
                    p.getId(),
                    p.getProducto().getId(),
                    p.getLista().getId(),
                    p.getMonto(),
                    p.getVigenciaDesde(),
                    p.getVigenciaHasta(),
                    p.estaAbierto());
        }
    }
}
