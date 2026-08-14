package dev.francogomez.comercio.core.auth;

public enum Rol {

    /** Administra el catálogo, las listas de precios y los puntos de venta. */
    ADMIN,

    /** Opera: registra ventas, movimientos de stock y notas de crédito. */
    VENDEDOR;

    /**
     * Spring Security espera los roles con el prefijo {@code ROLE_} cuando se comparan
     * con {@code hasRole(...)}. Se arma acá para que el prefijo no ande suelto por el
     * código ni haya que acordarse de agregarlo en cada lugar.
     */
    public String comoAuthority() {
        return "ROLE_" + name();
    }
}
