package dev.francogomez.comercio.reportes.proyeccion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface EventoProcesadoRepository extends JpaRepository<EventoProcesado, UUID> {

    /**
     * INSERT explícito en lugar de {@code save()}: el id del evento es asignado, no
     * generado, y ante eso Spring Data llama a {@code merge()}, que hace SELECT y luego
     * UPDATE si la fila ya existe. Nunca chocaría contra la clave primaria, que es
     * justamente lo que se busca acá para detectar un evento repetido.
     *
     * @return 1 si el evento es nuevo, 0 si ya estaba procesado
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO evento_procesado (event_id, tipo, procesado_en)
            VALUES (:eventId, :tipo, now())
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int marcarProcesado(@Param("eventId") UUID eventId, @Param("tipo") String tipo);
}
