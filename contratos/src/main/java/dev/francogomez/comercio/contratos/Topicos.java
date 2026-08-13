package dev.francogomez.comercio.contratos;

/**
 * Nombres de los tópicos de Kafka, compartidos entre quien publica y quien consume.
 * Viven en el módulo de contratos para que un cambio de nombre rompa la compilación de
 * ambos lados en vez de fallar en runtime con un consumidor escuchando la nada.
 */
public final class Topicos {

    public static final String VENTAS = "comercio.ventas";

    /** Adonde van los eventos que el consumidor no pudo procesar tras agotar reintentos. */
    public static final String VENTAS_DLT = "comercio.ventas.dlt";

    private Topicos() {
    }
}
