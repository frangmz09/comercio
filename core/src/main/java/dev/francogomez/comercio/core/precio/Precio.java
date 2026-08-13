package dev.francogomez.comercio.core.precio;

import dev.francogomez.comercio.core.producto.Producto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * El precio de un producto en una lista, acotado a una ventana temporal [desde, hasta).
 * Nunca se edita el monto de un precio ya vigente: se cierra la vigencia y se abre uno
 * nuevo. Así, reimprimir una venta de hace seis meses devuelve el precio de ese día y
 * no el de hoy.
 */
@Entity
@Table(name = "precio")
public class Precio {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lista_precio_id", nullable = false)
    private ListaPrecio lista;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal monto;

    @Column(name = "vigencia_desde", nullable = false)
    private Instant vigenciaDesde;

    /** {@code null} significa vigente sin fecha de cierre conocida. */
    @Column(name = "vigencia_hasta")
    private Instant vigenciaHasta;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Version
    private long version;

    protected Precio() {
        // JPA
    }

    public Precio(Producto producto, ListaPrecio lista, BigDecimal monto, Instant vigenciaDesde) {
        this.producto = producto;
        this.lista = lista;
        this.monto = monto;
        this.vigenciaDesde = vigenciaDesde;
        this.creadoEn = Instant.now();
    }

    /**
     * Cierra la vigencia en el instante en que arranca el precio siguiente. Como los
     * rangos son semiabiertos, el borde compartido no genera solapamiento.
     */
    public void cerrarEn(Instant momento) {
        if (!momento.isAfter(vigenciaDesde)) {
            throw new IllegalArgumentException(
                    "No se puede cerrar un precio en un instante anterior o igual a su inicio de vigencia");
        }
        this.vigenciaHasta = momento;
    }

    public boolean estaVigenteEn(Instant momento) {
        boolean yaEmpezo = !momento.isBefore(vigenciaDesde);
        boolean noTermino = vigenciaHasta == null || momento.isBefore(vigenciaHasta);
        return yaEmpezo && noTermino;
    }

    public boolean estaAbierto() {
        return vigenciaHasta == null;
    }

    public UUID getId() {
        return id;
    }

    public Producto getProducto() {
        return producto;
    }

    public ListaPrecio getLista() {
        return lista;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public Instant getVigenciaDesde() {
        return vigenciaDesde;
    }

    public Instant getVigenciaHasta() {
        return vigenciaHasta;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public long getVersion() {
        return version;
    }
}
