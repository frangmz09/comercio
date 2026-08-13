package dev.francogomez.comercio.core.stock;

import dev.francogomez.comercio.core.producto.Producto;
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
 * Un asiento del libro de stock. No tiene setters ni {@code @Version} a propósito: una
 * vez escrito no se toca. El saldo actual es una consecuencia de estos movimientos, no
 * al revés.
 */
@Entity
@Table(name = "movimiento_stock")
public class MovimientoStock {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoMovimiento tipo;

    /** Con signo ya aplicado: negativa en las salidas. */
    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal cantidad;

    @Column(nullable = false, length = 200)
    private String motivo;

    @Column(length = 100)
    private String referencia;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    protected MovimientoStock() {
        // JPA
    }

    public MovimientoStock(Producto producto, TipoMovimiento tipo, BigDecimal cantidadConSigno,
                           String motivo, String referencia) {
        this.producto = producto;
        this.tipo = tipo;
        this.cantidad = cantidadConSigno;
        this.motivo = motivo;
        this.referencia = referencia;
        this.creadoEn = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Producto getProducto() {
        return producto;
    }

    public TipoMovimiento getTipo() {
        return tipo;
    }

    public BigDecimal getCantidad() {
        return cantidad;
    }

    public String getMotivo() {
        return motivo;
    }

    public String getReferencia() {
        return referencia;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
