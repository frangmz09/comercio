package dev.francogomez.comercio.core.producto;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProductoRepository extends JpaRepository<Producto, UUID> {

    Optional<Producto> findBySku(String sku);

    boolean existsBySku(String sku);

    Page<Producto> findByCategoriaIgnoreCaseAndActivo(String categoria, boolean activo, Pageable pageable);

    Page<Producto> findByCategoriaIgnoreCase(String categoria, Pageable pageable);

    Page<Producto> findByActivo(boolean activo, Pageable pageable);
}
