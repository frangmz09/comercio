package dev.francogomez.comercio.core.comprobante;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComprobanteRepository extends JpaRepository<Comprobante, UUID> {

    /**
     * Trae el punto de venta en la misma consulta. Sin esto, armar la respuesta explota
     * con LazyInitializationException: el número formateado necesita el número del punto
     * de venta, y para cuando el controlador lo pide la sesión ya está cerrada.
     */
    @EntityGraph(attributePaths = "puntoVenta")
    List<Comprobante> findByVentaIdOrderByCreadoEnAsc(UUID ventaId);

    @EntityGraph(attributePaths = "puntoVenta")
    Optional<Comprobante> findByVentaIdAndTipo(UUID ventaId, TipoComprobante tipo);

    @EntityGraph(attributePaths = "puntoVenta")
    Optional<Comprobante> findWithPuntoVentaById(UUID id);

    boolean existsByVentaIdAndTipo(UUID ventaId, TipoComprobante tipo);

    @EntityGraph(attributePaths = "puntoVenta")
    List<Comprobante> findByPuntoVentaIdAndTipoOrderByNumeroAsc(UUID puntoVentaId, TipoComprobante tipo);
}
