package dev.francogomez.comercio.core.venta;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Una línea de venta guarda el precio unitario con el que se vendió, no una referencia
 * al precio del catálogo. Es una copia deliberada: el catálogo cambia, la venta no.
 */
@Entity
@Table(name = "linea_venta")
public class LineaVenta {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venta_id", nullable = false)
    private Venta venta;

    @Column(name = "producto_id", nullable = false)
    private UUID productoId;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal cantidad;

    @Column(name = "precio_unitario", nullable = false, precision = 14, scale = 2)
    private BigDecimal precioUnitario;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal;

    protected LineaVenta() {
        // JPA
    }

    LineaVenta(Venta venta, UUID productoId, BigDecimal cantidad, BigDecimal precioUnitario) {
        this.venta = venta;
        this.productoId = productoId;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
        // HALF_UP es la regla de redondeo comercial habitual y coincide con la escala 2
        // de la columna, así que el valor guardado es exactamente el que se calculó.
        this.subtotal = cantidad.multiply(precioUnitario).setScale(2, RoundingMode.HALF_UP);
    }

    public UUID getId() {
        return id;
    }

    public Venta getVenta() {
        return venta;
    }

    public UUID getProductoId() {
        return productoId;
    }

    public BigDecimal getCantidad() {
        return cantidad;
    }

    public BigDecimal getPrecioUnitario() {
        return precioUnitario;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }
}
