package dev.francogomez.comercio.core.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<EventoOutbox, UUID> {

    /**
     * Toma un lote de eventos pendientes bloqueándolos y salteando los que otra instancia
     * ya tenga tomados. El {@code SKIP LOCKED} es lo que permite correr varias réplicas
     * del servicio publicando en paralelo sin que se pisen ni se queden esperando: cada
     * una se lleva un lote distinto.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select e from EventoOutbox e where e.publicadoEn is null order by e.creadoEn asc")
    List<EventoOutbox> tomarPendientes(Limit limite);

    long countByPublicadoEnIsNull();
}
