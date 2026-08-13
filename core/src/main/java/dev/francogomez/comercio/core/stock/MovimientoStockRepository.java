package dev.francogomez.comercio.core.stock;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MovimientoStockRepository extends JpaRepository<MovimientoStock, UUID> {

    Page<MovimientoStock> findByProductoIdOrderByCreadoEnDesc(UUID productoId, Pageable pageable);
}
