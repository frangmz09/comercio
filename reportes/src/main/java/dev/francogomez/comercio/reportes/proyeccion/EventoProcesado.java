package dev.francogomez.comercio.reportes.proyeccion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Marca de que un evento ya fue aplicado. Kafka entrega at-least-once, así que el mismo
 * evento puede llegar dos veces: un rebalanceo del grupo, un reintento del publisher, un
 * commit de offset que no alcanzó a persistir. Sin esta marca, un duplicado sumaría la
 * venta dos veces al reporte.
 */
@Entity
@Table(name = "evento_procesado")
public class EventoProcesado {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(nullable = false, length = 60)
    private String tipo;

    @Column(name = "procesado_en", nullable = false, updatable = false)
    private Instant procesadoEn;

    protected EventoProcesado() {
        // JPA
    }

    public EventoProcesado(UUID eventId, String tipo) {
        this.eventId = eventId;
        this.tipo = tipo;
        this.procesadoEn = Instant.now();
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getTipo() {
        return tipo;
    }

    public Instant getProcesadoEn() {
        return procesadoEn;
    }
}
