package dev.francogomez.comercio.core.venta;

import dev.francogomez.comercio.core.precio.ListaPrecio;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "venta")
public class Venta {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lista_precio_id", nullable = false)
    private ListaPrecio lista;

    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LineaVenta> lineas = new ArrayList<>();

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoVenta estado;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Version
    private long version;

    protected Venta() {
        // JPA
    }

    public Venta(ListaPrecio lista) {
        this.lista = lista;
        this.estado = EstadoVenta.CONFIRMADA;
        this.creadoEn = Instant.now();
    }

    /**
     * Agrega una línea con el precio ya resuelto. El total se recalcula sobre las líneas
     * en vez de irse acumulando: si alguna vez se permite quitar una línea, el total
     * sigue siendo correcto sin tener que acordarse de restar.
     */
    public LineaVenta agregarLinea(UUID productoId, BigDecimal cantidad, BigDecimal precioUnitario) {
        LineaVenta linea = new LineaVenta(this, productoId, cantidad, precioUnitario);
        lineas.add(linea);
        recalcularTotal();
        return linea;
    }

    /**
     * La venta anulada conserva sus líneas y su total: lo que la revierte es la nota de
     * crédito, y para poder explicar el saldo hay que poder ver las dos cosas.
     */
    public void anular() {
        if (estado == EstadoVenta.ANULADA) {
            throw new IllegalStateException("La venta " + id + " ya está anulada");
        }
        this.estado = EstadoVenta.ANULADA;
    }

    private void recalcularTotal() {
        this.total = lineas.stream()
                .map(LineaVenta::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public UUID getId() {
        return id;
    }

    public ListaPrecio getLista() {
        return lista;
    }

    public List<LineaVenta> getLineas() {
        return List.copyOf(lineas);
    }

    public BigDecimal getTotal() {
        return total;
    }

    public EstadoVenta getEstado() {
        return estado;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public long getVersion() {
        return version;
    }
}
