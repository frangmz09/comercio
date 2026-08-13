package dev.francogomez.comercio.reportes.proyeccion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductoVendidoRepository extends JpaRepository<ProductoVendido, String> {

    List<ProductoVendido> findTop10ByOrderByUnidadesDesc();
}
