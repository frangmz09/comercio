package dev.francogomez.comercio.core.venta;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VentaRepository extends JpaRepository<Venta, UUID> {

    /** Trae las líneas en la misma consulta: la respuesta siempre las necesita. */
    @EntityGraph(attributePaths = "lineas")
    Optional<Venta> findWithLineasById(UUID id);

    @EntityGraph(attributePaths = "lineas")
    Page<Venta> findAllBy(Pageable pageable);
}
