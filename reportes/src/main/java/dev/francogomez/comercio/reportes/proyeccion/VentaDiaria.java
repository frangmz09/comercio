package dev.francogomez.comercio.reportes.proyeccion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "venta_diaria")
public class VentaDiaria {

    @Id
    private LocalDate fecha;

    @Column(name = "cantidad_ventas", nullable = false)
    private long cantidadVentas;

    @Column(name = "total_vendido", nullable = false, precision = 16, scale = 2)
    private BigDecimal totalVendido = BigDecimal.ZERO;

    @Column(name = "total_anulado", nullable = false, precision = 16, scale = 2)
    private BigDecimal totalAnulado = BigDecimal.ZERO;

    protected VentaDiaria() {
        // JPA
    }

    public VentaDiaria(LocalDate fecha) {
        this.fecha = fecha;
        this.cantidadVentas = 0;
        this.totalVendido = BigDecimal.ZERO;
        this.totalAnulado = BigDecimal.ZERO;
    }

    public void sumarVenta(BigDecimal total) {
        this.cantidadVentas++;
        this.totalVendido = this.totalVendido.add(total);
    }

    /**
     * La anulación se acumula aparte en lugar de restarse de lo vendido: perder el dato
     * de cuánto se vendió y cuánto se devolvió haría imposible responder por qué el neto
     * bajó.
     */
    public void sumarAnulacion(BigDecimal total) {
        this.totalAnulado = this.totalAnulado.add(total);
    }

    public BigDecimal getNeto() {
        return totalVendido.subtract(totalAnulado);
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public long getCantidadVentas() {
        return cantidadVentas;
    }

    public BigDecimal getTotalVendido() {
        return totalVendido;
    }

    public BigDecimal getTotalAnulado() {
        return totalAnulado;
    }
}
