package dev.francogomez.comercio.core.precio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Un mismo producto tiene precios distintos según a quién se le venda (minorista,
 * mayorista, un convenio puntual). La lista es lo que separa esos universos.
 */
@Entity
@Table(name = "lista_precio")
public class ListaPrecio {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, unique = true, length = 30)
    private String codigo;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false)
    private boolean activa = true;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Version
    private long version;

    protected ListaPrecio() {
        // JPA
    }

    public ListaPrecio(String codigo, String nombre) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.activa = true;
        this.creadoEn = Instant.now();
    }

    public void renombrar(String nombre) {
        this.nombre = nombre;
    }

    public void desactivar() {
        this.activa = false;
    }

    public void activar() {
        this.activa = true;
    }

    public UUID getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public boolean isActiva() {
        return activa;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public long getVersion() {
        return version;
    }
}
