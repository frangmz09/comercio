package dev.francogomez.comercio.core.venta;

public enum EstadoVenta {

    CONFIRMADA,

    /** Revertida por una nota de crédito. La venta no se borra: queda con su historia. */
    ANULADA
}
