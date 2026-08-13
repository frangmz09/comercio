package dev.francogomez.comercio.core.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Un evento esperando ser publicado. Se escribe en la misma transacción que el cambio
 * de negocio que lo origina, de modo que el evento existe si y solo si el cambio
 * existe.
 *
 * <p>Publicar a Kafka directamente desde el servicio sería un dual-write sobre dos
 * sistemas que no comparten transacción: la venta puede commitear y el publish fallar
 * —evento perdido, el reporte nunca se entera— o el publish puede salir y la
 * transacción revertirse —evento fantasma, el reporte cuenta una venta que no ocurrió.
 */
@Entity
@Table(name = "outbox")
public class EventoOutbox {

    @Id
    @UuidGenerator
    private UUID id;

    /**
     * Id del agregado que originó el evento. Se usa como clave de partición en Kafka,
     * para que los eventos de una misma venta lleguen ordenados al consumidor.
     */
    @Column(name = "agregado_id", nullable = false)
    private UUID agregadoId;

    @Column(nullable = false, length = 60)
    private String tipo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "publicado_en")
    private Instant publicadoEn;

    @Column(nullable = false)
    private int intentos;

    @Column(name = "ultimo_error")
    private String ultimoError;

    protected EventoOutbox() {
        // JPA
    }

    public EventoOutbox(UUID agregadoId, String tipo, String payload) {
        this.agregadoId = agregadoId;
        this.tipo = tipo;
        this.payload = payload;
        this.creadoEn = Instant.now();
        this.intentos = 0;
    }

    public void marcarPublicado() {
        this.publicadoEn = Instant.now();
        this.ultimoError = null;
    }

    /**
     * Registra un intento fallido. No se borra ni se descarta el evento: mientras
     * {@code publicadoEn} siga en null, el publisher lo va a volver a tomar.
     */
    public void registrarFallo(String error) {
        this.intentos++;
        // El detalle puede ser enorme (stack traces anidados) y la columna es informativa.
        this.ultimoError = error != null && error.length() > 1000 ? error.substring(0, 1000) : error;
    }

    public boolean estaPendiente() {
        return publicadoEn == null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAgregadoId() {
        return agregadoId;
    }

    public String getTipo() {
        return tipo;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getPublicadoEn() {
        return publicadoEn;
    }

    public int getIntentos() {
        return intentos;
    }

    public String getUltimoError() {
        return ultimoError;
    }
}
