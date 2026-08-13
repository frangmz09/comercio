package dev.francogomez.comercio.core.stock;

import dev.francogomez.comercio.core.shared.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Saldo materializado por producto. Existe para dos cosas: evitar recorrer todo el
 * historial de movimientos en cada venta, y —sobre todo— dar una fila concreta sobre la
 * cual serializar la concurrencia con un SELECT FOR UPDATE.
 */
@Entity
@Table(name = "saldo_stock")
public class SaldoStock {

    @Id
    @Column(name = "producto_id")
    private UUID productoId;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal cantidad = BigDecimal.ZERO;

    @Version
    private long version;

    protected SaldoStock() {
        // JPA
    }

    public SaldoStock(UUID productoId) {
        this.productoId = productoId;
        this.cantidad = BigDecimal.ZERO;
    }

    /**
     * Aplica un movimiento con signo. Acá es donde se hace cumplir que el stock no
     * pueda quedar negativo: la validación vive junto al dato que protege, no en el
     * servicio, para que no haya forma de modificar el saldo salteándola.
     */
    public void aplicar(BigDecimal delta) {
        BigDecimal resultante = cantidad.add(delta);
        if (resultante.signum() < 0) {
            throw new ConflictException(
                    "Stock insuficiente: hay %s y se intentan descontar %s"
                            .formatted(cantidad.toPlainString(), delta.abs().toPlainString()));
        }
        this.cantidad = resultante;
    }

    public UUID getProductoId() {
        return productoId;
    }

    public BigDecimal getCantidad() {
        return cantidad;
    }

    public long getVersion() {
        return version;
    }
}
