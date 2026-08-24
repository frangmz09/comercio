package dev.francogomez.comercio.core.stock;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class StockDtos {

    private StockDtos() {
    }

    public record MovimientoRequest(
            @NotNull
            @Schema(description = "Id que devolvió POST /api/v1/productos",
                    example = "9b1f2c3d-4e5a-4b6c-8d7e-0f1a2b3c4d5e")
            UUID productoId,

            @NotNull TipoMovimiento tipo,

            /**
             * En ENTRADA y SALIDA el signo lo impone el tipo, así que da igual cómo se
             * mande. En AJUSTE se respeta: un negativo expresa un faltante de recuento.
             */
            @NotNull
            @Digits(integer = 11, fraction = 3)
            @Schema(example = "24")
            BigDecimal cantidad,

            @NotBlank @Size(max = 200)
            @Schema(description = "Por qué se movió el stock; queda en el historial",
                    example = "Recepción de mercadería")
            String motivo,

            @Size(max = 100)
            @Schema(description = "Documento que respalda el movimiento, si lo hay",
                    example = "REM-0001-00004821")
            String referencia) {

        /**
         * Un movimiento de cantidad cero no significa nada y la base lo rechaza por
         * CHECK. Validarlo acá lo convierte en un 400 explicativo en vez de un 500.
         */
        // hidden: es una regla de validación, no un campo que el cliente mande. Sin esto
        // springdoc lo publica en el schema y Swagger lo ofrece para completar.
        @AssertTrue(message = "la cantidad no puede ser cero")
        @Schema(hidden = true)
        public boolean isCantidadDistintaDeCero() {
            return cantidad == null || cantidad.signum() != 0;
        }
    }

    public record MovimientoResponse(
            UUID id,
            UUID productoId,
            TipoMovimiento tipo,
            BigDecimal cantidad,
            String motivo,
            String referencia,
            Instant creadoEn) {

        public static MovimientoResponse from(MovimientoStock m) {
            return new MovimientoResponse(
                    m.getId(), m.getProducto().getId(), m.getTipo(), m.getCantidad(),
                    m.getMotivo(), m.getReferencia(), m.getCreadoEn());
        }
    }

    public record SaldoResponse(UUID productoId, BigDecimal cantidad) {
    }
}
