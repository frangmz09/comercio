package dev.francogomez.comercio.core.comprobante;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Una caja, una sucursal o un canal de venta. Cada uno numera sus comprobantes por
 * separado: la factura 15 de la caja 1 y la factura 15 de la caja 2 conviven sin
 * pisarse.
 */
@Entity
@Table(name = "punto_venta")
public class PuntoVenta {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, unique = true)
    private int numero;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    protected PuntoVenta() {
        // JPA
    }

    public PuntoVenta(int numero, String nombre) {
        this.numero = numero;
        this.nombre = nombre;
        this.activo = true;
        this.creadoEn = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public int getNumero() {
        return numero;
    }

    public String getNombre() {
        return nombre;
    }

    public boolean isActivo() {
        return activo;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
