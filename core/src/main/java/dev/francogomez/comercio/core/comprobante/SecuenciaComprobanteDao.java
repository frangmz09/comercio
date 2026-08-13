package dev.francogomez.comercio.core.comprobante;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Reserva de números de comprobante.
 *
 * <p>Es el único punto del proyecto que usa JDBC directo en lugar de JPA, y es
 * deliberado: la operación es un UPSERT que además devuelve el valor reservado, algo que
 * JPA no puede expresar. Hacerlo con un SELECT FOR UPDATE seguido de un UPDATE también
 * funcionaría, pero serían dos viajes a la base para lo que Postgres resuelve en una
 * sola sentencia atómica.
 *
 * <p>Tampoco se usa una SEQUENCE de Postgres: las secuencias no son transaccionales y
 * dejan huecos cuando una transacción se revierte. En un comprobante, un hueco en la
 * numeración no es un detalle estético — es una inconsistencia que después hay que
 * explicar.
 */
@Repository
public class SecuenciaComprobanteDao {

    private final JdbcTemplate jdbc;

    public SecuenciaComprobanteDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Devuelve el próximo número para el par (punto de venta, tipo), creando la secuencia
     * si es el primer comprobante que se emite. La fila queda bloqueada hasta el fin de
     * la transacción, así que si esta se revierte el número vuelve a estar disponible y
     * no se pierde.
     */
    public long reservarNumero(UUID puntoVentaId, TipoComprobante tipo) {
        Long numero = jdbc.queryForObject("""
                INSERT INTO secuencia_comprobante (punto_venta_id, tipo, proximo_numero)
                VALUES (?, ?, 2)
                ON CONFLICT (punto_venta_id, tipo)
                DO UPDATE SET proximo_numero = secuencia_comprobante.proximo_numero + 1
                RETURNING proximo_numero - 1
                """, Long.class, puntoVentaId, tipo.name());

        if (numero == null) {
            throw new IllegalStateException(
                    "La reserva de número no devolvió valor para el punto de venta " + puntoVentaId);
        }
        return numero;
    }
}
