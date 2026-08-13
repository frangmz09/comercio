package dev.francogomez.comercio.core.venta;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class VentaDtos {

    private VentaDtos() {
    }

    public record VentaRequest(
            @NotNull UUID listaPrecioId,
            @NotEmpty(message = "la venta debe tener al menos una línea")
            @Valid List<LineaRequest> lineas) {
    }

    public record LineaRequest(
            @NotNull UUID productoId,

            @NotNull
            @DecimalMin(value = "0.001", message = "la cantidad debe ser mayor a cero")
            @Digits(integer = 11, fraction = 3)
            BigDecimal cantidad) {
    }

    public record LineaResponse(
            UUID productoId,
            BigDecimal cantidad,
            BigDecimal precioUnitario,
            BigDecimal subtotal) {

        public static LineaResponse from(LineaVenta l) {
            return new LineaResponse(l.getProductoId(), l.getCantidad(), l.getPrecioUnitario(), l.getSubtotal());
        }
    }

    public record VentaResponse(
            UUID id,
            UUID listaPrecioId,
            EstadoVenta estado,
            BigDecimal total,
            Instant creadoEn,
            List<LineaResponse> lineas) {

        public static VentaResponse from(Venta v) {
            return new VentaResponse(
                    v.getId(),
                    v.getLista().getId(),
                    v.getEstado(),
                    v.getTotal(),
                    v.getCreadoEn(),
                    v.getLineas().stream().map(LineaResponse::from).toList());
        }
    }
}
