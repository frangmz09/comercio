package dev.francogomez.comercio.core.stock;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SaldoStockRepository extends JpaRepository<SaldoStock, UUID> {

    /**
     * SELECT ... FOR UPDATE sobre el saldo. Es el corazón de la concurrencia del módulo:
     * dos ventas simultáneas del mismo producto se serializan acá, y la segunda ve el
     * saldo ya descontado por la primera en lugar de leer un valor viejo y sobrevender.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SaldoStock s where s.productoId = :productoId")
    Optional<SaldoStock> findParaActualizar(@Param("productoId") UUID productoId);

    /**
     * Crea la fila de saldo si todavía no existe, sin fallar cuando otra transacción se
     * adelantó. Hace falta porque el lock de arriba no puede tomarse sobre una fila que
     * no existe: si dos altas de stock simultáneas fueran las primeras del producto,
     * ambas intentarían insertar y una moriría por clave duplicada.
     */
    @Modifying
    @Query(value = """
            INSERT INTO saldo_stock (producto_id, cantidad, version)
            VALUES (:productoId, 0, 0)
            ON CONFLICT (producto_id) DO NOTHING
            """, nativeQuery = true)
    void crearSiNoExiste(@Param("productoId") UUID productoId);
}
