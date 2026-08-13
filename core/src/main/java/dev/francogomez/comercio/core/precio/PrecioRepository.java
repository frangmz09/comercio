package dev.francogomez.comercio.core.precio;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrecioRepository extends JpaRepository<Precio, UUID> {

    /**
     * El precio que rige para un producto en una lista a un instante dado. Con la
     * constraint de exclusión activa no puede haber más de uno, así que devolver un
     * Optional es exacto y no una simplificación.
     */
    @Query("""
            select p from Precio p
            where p.producto.id = :productoId
              and p.lista.id = :listaId
              and p.vigenciaDesde <= :momento
              and (p.vigenciaHasta is null or p.vigenciaHasta > :momento)
            """)
    Optional<Precio> findVigente(@Param("productoId") UUID productoId,
                                 @Param("listaId") UUID listaId,
                                 @Param("momento") Instant momento);

    /**
     * Igual que {@link #findVigente} pero tomando un lock de escritura sobre la fila.
     * Se usa al dar de alta un precio nuevo: entre leer el vigente y cerrarlo no puede
     * colarse otra transacción haciendo lo mismo.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p from Precio p
            where p.producto.id = :productoId
              and p.lista.id = :listaId
              and (p.vigenciaHasta is null or p.vigenciaHasta > :momento)
              and p.vigenciaDesde <= :momento
            """)
    Optional<Precio> findVigenteParaActualizar(@Param("productoId") UUID productoId,
                                               @Param("listaId") UUID listaId,
                                               @Param("momento") Instant momento);

    @Query("""
            select p from Precio p
            where p.producto.id = :productoId and p.lista.id = :listaId
            order by p.vigenciaDesde desc
            """)
    List<Precio> findHistorial(@Param("productoId") UUID productoId, @Param("listaId") UUID listaId);
}
