package dev.francogomez.comercio.core.venta;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ClaveIdempotenciaRepository extends JpaRepository<ClaveIdempotencia, String> {

    /**
     * INSERT explícito, y no {@code save()}, por una razón que no se ve a simple vista:
     * la clave es un id <em>asignado</em>, no generado. Ante eso Spring Data considera
     * que la entidad no es nueva y llama a {@code merge()}, que hace SELECT y después
     * UPDATE si la fila ya existe. O sea: nunca viola la primary key y el segundo
     * escritor pisa al primero en silencio, justo lo contrario de lo que se busca acá.
     *
     * <p>Con este INSERT, un duplicado explota como {@code DataIntegrityViolationException}
     * y la base queda siendo la que garantiza que una clave se use una sola vez.
     */
    // flushAutomatically: la venta y sus movimientos todavía pueden estar pendientes en
    // el contexto de persistencia, y esta fila los referencia por clave foránea.
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO clave_idempotencia (clave, huella, venta_id, creado_en)
            VALUES (:clave, :huella, :ventaId, now())
            """, nativeQuery = true)
    void insertar(@Param("clave") String clave,
                  @Param("huella") String huella,
                  @Param("ventaId") UUID ventaId);
}
