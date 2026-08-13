package dev.francogomez.comercio.core.venta;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Registro de una clave de idempotencia ya usada. La clave es la PK: es la base la que
 * impide que la misma operación se registre dos veces, no una comprobación previa en
 * memoria que dos hilos podrían pasar a la vez.
 */
@Entity
@Table(name = "clave_idempotencia")
public class ClaveIdempotencia {

    @Id
    @Column(length = 120)
    private String clave;

    @Column(nullable = false, length = 64)
    private String huella;

    @Column(name = "venta_id", nullable = false)
    private UUID ventaId;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    protected ClaveIdempotencia() {
        // JPA
    }

    public ClaveIdempotencia(String clave, String huella, UUID ventaId) {
        this.clave = clave;
        this.huella = huella;
        this.ventaId = ventaId;
        this.creadoEn = Instant.now();
    }

    public boolean coincideCon(String otraHuella) {
        return huella.equals(otraHuella);
    }

    public String getClave() {
        return clave;
    }

    public String getHuella() {
        return huella;
    }

    public UUID getVentaId() {
        return ventaId;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
