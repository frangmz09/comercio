package dev.francogomez.comercio.core.comprobante;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComprobanteRepository extends JpaRepository<Comprobante, UUID> {

    List<Comprobante> findByVentaIdOrderByCreadoEnAsc(UUID ventaId);

    Optional<Comprobante> findByVentaIdAndTipo(UUID ventaId, TipoComprobante tipo);

    boolean existsByVentaIdAndTipo(UUID ventaId, TipoComprobante tipo);

    List<Comprobante> findByPuntoVentaIdAndTipoOrderByNumeroAsc(UUID puntoVentaId, TipoComprobante tipo);
}
