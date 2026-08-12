package dev.francogomez.comercio.core.producto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "producto")
public class Producto {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, unique = true, length = 40)
    private String sku;

    @Column(nullable = false, length = 200)
    private String nombre;

    @Column(length = 100)
    private String categoria;

    @Column(nullable = false, length = 20)
    private String unidad;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Version
    private long version;

    protected Producto() {
        // JPA
    }

    public Producto(String sku, String nombre, String categoria, String unidad) {
        this.sku = sku;
        this.nombre = nombre;
        this.categoria = categoria;
        this.unidad = unidad;
        this.activo = true;
        this.creadoEn = Instant.now();
    }

    public void actualizar(String nombre, String categoria, String unidad) {
        this.nombre = nombre;
        this.categoria = categoria;
        this.unidad = unidad;
    }

    public void desactivar() {
        this.activo = false;
    }

    public void activar() {
        this.activo = true;
    }

    public UUID getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getNombre() {
        return nombre;
    }

    public String getCategoria() {
        return categoria;
    }

    public String getUnidad() {
        return unidad;
    }

    public boolean isActivo() {
        return activo;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public long getVersion() {
        return version;
    }
}
