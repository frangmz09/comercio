package dev.francogomez.comercio.core.stock;

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
            @NotNull UUID productoId,

            @NotNull TipoMovimiento tipo,

            /**
             * En ENTRADA y SALIDA el signo lo impone el tipo, así que da igual cómo se
             * mande. En AJUSTE se respeta: un negativo expresa un faltante de recuento.
             */
            @NotNull
            @Digits(integer = 11, fraction = 3)
            BigDecimal cantidad,

            @NotBlank @Size(max = 200) String motivo,

            @Size(max = 100) String referencia) {

        /**
         * Un movimiento de cantidad cero no significa nada y la base lo rechaza por
         * CHECK. Validarlo acá lo convierte en un 400 explicativo en vez de un 500.
         */
        @AssertTrue(message = "la cantidad no puede ser cero")
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
