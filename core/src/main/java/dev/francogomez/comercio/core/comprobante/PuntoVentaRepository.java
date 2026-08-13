package dev.francogomez.comercio.core.comprobante;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PuntoVentaRepository extends JpaRepository<PuntoVenta, UUID> {

    Optional<PuntoVenta> findByNumero(int numero);

    boolean existsByNumero(int numero);
}
