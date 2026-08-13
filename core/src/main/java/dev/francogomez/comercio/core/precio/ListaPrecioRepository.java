package dev.francogomez.comercio.core.precio;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ListaPrecioRepository extends JpaRepository<ListaPrecio, UUID> {

    Optional<ListaPrecio> findByCodigoIgnoreCase(String codigo);

    boolean existsByCodigoIgnoreCase(String codigo);

    Page<ListaPrecio> findByActiva(boolean activa, Pageable pageable);
}
