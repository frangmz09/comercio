package dev.francogomez.comercio.reportes.proyeccion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Acumulado por producto. Se identifica por SKU y no por el id interno del catálogo:
 * este servicio tiene su propia base y nunca vio la tabla producto de {@code core}.
 * Acoplarse a un id ajeno sería depender de la representación interna de otro servicio.
 */
@Entity
@Table(name = "producto_vendido")
public class ProductoVendido {

    @Id
    @Column(length = 40)
    private String sku;

    @Column(nullable = false, precision = 16, scale = 3)
    private BigDecimal unidades = BigDecimal.ZERO;

    @Column(name = "total_facturado", nullable = false, precision = 16, scale = 2)
    private BigDecimal totalFacturado = BigDecimal.ZERO;

    @Column(name = "ultima_venta")
    private Instant ultimaVenta;

    protected ProductoVendido() {
        // JPA
    }

    public ProductoVendido(String sku) {
        this.sku = sku;
        this.unidades = BigDecimal.ZERO;
        this.totalFacturado = BigDecimal.ZERO;
    }

    public void acumular(BigDecimal cantidad, BigDecimal importe, Instant cuando) {
        this.unidades = this.unidades.add(cantidad);
        this.totalFacturado = this.totalFacturado.add(importe);
        if (ultimaVenta == null || cuando.isAfter(ultimaVenta)) {
            this.ultimaVenta = cuando;
        }
    }

    public String getSku() {
        return sku;
    }

    public BigDecimal getUnidades() {
        return unidades;
    }

    public BigDecimal getTotalFacturado() {
        return totalFacturado;
    }

    public Instant getUltimaVenta() {
        return ultimaVenta;
    }
}
