package dev.francogomez.comercio.core.comprobante;

public enum TipoComprobante {

    FACTURA,

    /** Acompaña la mercadería; no tiene efecto sobre la cuenta corriente. */
    REMITO,

    /** Revierte una venta ya facturada. Siempre apunta al comprobante que anula. */
    NOTA_CREDITO
}
