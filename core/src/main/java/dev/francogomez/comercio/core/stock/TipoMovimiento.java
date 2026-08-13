package dev.francogomez.comercio.core.stock;

import java.math.BigDecimal;

public enum TipoMovimiento {

    /** Compra a proveedor, devolución de cliente, carga inicial. Suma. */
    ENTRADA,

    /** Venta, rotura, consumo interno. Resta. */
    SALIDA,

    /** Corrección posterior a un recuento físico. Puede sumar o restar. */
    AJUSTE;

    /**
     * Lleva una cantidad expresada en positivo al signo que le corresponde al tipo.
     * El AJUSTE es el único que respeta el signo que le mandan, porque un recuento
     * puede tanto encontrar mercadería de más como de menos.
     */
    public BigDecimal aplicarSigno(BigDecimal cantidad) {
        return switch (this) {
            case ENTRADA -> cantidad.abs();
            case SALIDA -> cantidad.abs().negate();
            case AJUSTE -> cantidad;
        };
    }
}
