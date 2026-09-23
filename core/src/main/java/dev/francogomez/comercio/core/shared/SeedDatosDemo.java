package dev.francogomez.comercio.core.shared;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * Carga un producto con precio y stock, una lista de precios y un punto de venta, para
 * que la demo se pueda recorrer desde Swagger UI sin armar nada antes.
 *
 * <p>Los ids son fijos y coinciden con los {@code example} de los DTOs: "Try it out"
 * propone justamente estos, así que registrar una venta sale con solo apretar Execute.
 * Los códigos (SKU, lista, número de caja) son otros que los de los ejemplos de alta,
 * para que esos altas no choquen con lo precargado.
 *
 * <p>Va por SQL y no por una migración porque son datos de demostración: una migración
 * los dejaría en cualquier base donde corra el esquema.
 */
@Configuration
@ConditionalOnProperty(name = "comercio.demo.seed-datos", havingValue = "true", matchIfMissing = true)
public class SeedDatosDemo {

    public static final UUID PRODUCTO = UUID.fromString("9b1f2c3d-4e5a-4b6c-8d7e-0f1a2b3c4d5e");
    public static final UUID LISTA_PRECIO = UUID.fromString("1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f");
    public static final UUID PUNTO_VENTA = UUID.fromString("2d3e4f5a-6b7c-4d8e-9f0a-1b2c3d4e5f6a");

    private static final Logger log = LoggerFactory.getLogger(SeedDatosDemo.class);

    @Bean
    public ApplicationRunner crearDatosDeDemo(JdbcTemplate jdbc, TransactionTemplate tx) {
        return args -> {
            Integer existe = jdbc.queryForObject(
                    "SELECT count(*) FROM producto WHERE id = ?", Integer.class, PRODUCTO);
            if (existe != null && existe > 0) {
                return;
            }
            try {
                tx.executeWithoutResult(status -> insertar(jdbc));
            } catch (DataIntegrityViolationException ex) {
                // Una base con datos previos puede tener ya ese SKU, código o número de
                // caja. La demo queda sin precarga, pero la API tiene que levantar igual.
                log.warn("No se cargaron los datos de demo: chocan con datos existentes ({})",
                        ex.getMostSpecificCause().getMessage());
                return;
            }
            log.info("Datos de demo creados: producto {}, lista {}, punto de venta {}",
                    PRODUCTO, LISTA_PRECIO, PUNTO_VENTA);
        };
    }

    private static void insertar(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO producto (id, sku, nombre, categoria, unidad)
                VALUES (?, 'DEMO-ACE-900', 'Aceite de girasol 900ml', 'Almacén', 'unidad')""",
                PRODUCTO);
        jdbc.update("""
                INSERT INTO lista_precio (id, codigo, nombre)
                VALUES (?, 'DEMO', 'Lista de la demo')""", LISTA_PRECIO);
        jdbc.update("""
                INSERT INTO precio (producto_id, lista_precio_id, monto, vigencia_desde)
                VALUES (?, ?, 2450.00, now())""", PRODUCTO, LISTA_PRECIO);
        jdbc.update("""
                INSERT INTO punto_venta (id, numero, nombre)
                VALUES (?, 3, 'Caja 3')""", PUNTO_VENTA);
        // El saldo se deriva de los movimientos: se cargan los dos, coherentes.
        jdbc.update("""
                INSERT INTO movimiento_stock (producto_id, tipo, cantidad, motivo)
                VALUES (?, 'ENTRADA', 100, 'Stock inicial de la demo')""", PRODUCTO);
        jdbc.update("INSERT INTO saldo_stock (producto_id, cantidad) VALUES (?, 100)", PRODUCTO);
    }
}
