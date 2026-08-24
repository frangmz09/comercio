package dev.francogomez.comercio.core.venta;

import dev.francogomez.comercio.core.comprobante.Comprobante;
import dev.francogomez.comercio.core.comprobante.TipoComprobante;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class VentaDtos {

    private VentaDtos() {
    }

    public record VentaRequest(
            @NotNull
            @Schema(description = "Lista de la que se toma el precio vigente de cada producto; "
                    + "id que devolvió POST /api/v1/listas-precio",
                    example = "1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f")
            UUID listaPrecioId,

            @NotNull
            @Schema(description = "Punto de venta que numera la factura; "
                    + "id que devolvió POST /api/v1/puntos-venta",
                    example = "2d3e4f5a-6b7c-4d8e-9f0a-1b2c3d4e5f6a")
            UUID puntoVentaId,

            @NotEmpty(message = "la venta debe tener al menos una línea")
            @Valid List<LineaRequest> lineas) {
    }

    public record NotaCreditoRequest(
            @NotNull
            @Schema(description = "Punto de venta que numera la nota de crédito",
                    example = "2d3e4f5a-6b7c-4d8e-9f0a-1b2c3d4e5f6a")
            UUID puntoVentaId,

            @Size(max = 200)
            @Schema(example = "Devolución del cliente")
            String motivo) {
    }

    public record LineaRequest(
            @NotNull
            @Schema(description = "Id que devolvió POST /api/v1/productos",
                    example = "9b1f2c3d-4e5a-4b6c-8d7e-0f1a2b3c4d5e")
            UUID productoId,

            @NotNull
            @DecimalMin(value = "0.001", message = "la cantidad debe ser mayor a cero")
            @Digits(integer = 11, fraction = 3)
            @Schema(example = "2")
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
            List<LineaResponse> lineas,
            List<ComprobanteResponse> comprobantes) {

        public static VentaResponse from(Venta v, List<Comprobante> comprobantes) {
            return new VentaResponse(
                    v.getId(),
                    v.getLista().getId(),
                    v.getEstado(),
                    v.getTotal(),
                    v.getCreadoEn(),
                    v.getLineas().stream().map(LineaResponse::from).toList(),
                    comprobantes.stream().map(ComprobanteResponse::from).toList());
        }
    }

    public record ComprobanteResponse(
            UUID id,
            TipoComprobante tipo,
            String numero,
            BigDecimal total,
            UUID revierteA,
            Instant creadoEn) {

        public static ComprobanteResponse from(Comprobante c) {
            return new ComprobanteResponse(
                    c.getId(), c.getTipo(), c.getNumeroFormateado(),
                    c.getTotal(), c.getRevierteA(), c.getCreadoEn());
        }
    }
}
