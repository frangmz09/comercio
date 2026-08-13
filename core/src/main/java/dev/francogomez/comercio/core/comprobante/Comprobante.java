package dev.francogomez.comercio.core.comprobante;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Un comprobante emitido. No tiene setters: una vez emitido no se corrige ni se anula
 * editándolo. Para revertirlo se emite una nota de crédito que lo apunta.
 *
 * <p>La venta se referencia por id y no con una asociación JPA, para que el feature de
 * comprobantes no quede atado al de ventas más allá del dato que necesita.
 */
@Entity
@Table(name = "comprobante")
public class Comprobante {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "punto_venta_id", nullable = false)
    private PuntoVenta puntoVenta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoComprobante tipo;

    @Column(nullable = false)
    private long numero;

    @Column(name = "venta_id", nullable = false)
    private UUID ventaId;

    @Column(name = "revierte_a")
    private UUID revierteA;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    protected Comprobante() {
        // JPA
    }

    public Comprobante(PuntoVenta puntoVenta, TipoComprobante tipo, long numero,
                       UUID ventaId, BigDecimal total, UUID revierteA) {
        this.puntoVenta = puntoVenta;
        this.tipo = tipo;
        this.numero = numero;
        this.ventaId = ventaId;
        this.total = total;
        this.revierteA = revierteA;
        this.creadoEn = Instant.now();
    }

    /** Numeración legible al estilo de los comprobantes argentinos: 0001-00000015. */
    public String getNumeroFormateado() {
        return "%04d-%08d".formatted(puntoVenta.getNumero(), numero);
    }

    public UUID getId() {
        return id;
    }

    public PuntoVenta getPuntoVenta() {
        return puntoVenta;
    }

    public TipoComprobante getTipo() {
        return tipo;
    }

    public long getNumero() {
        return numero;
    }

    public UUID getVentaId() {
        return ventaId;
    }

    public UUID getRevierteA() {
        return revierteA;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
