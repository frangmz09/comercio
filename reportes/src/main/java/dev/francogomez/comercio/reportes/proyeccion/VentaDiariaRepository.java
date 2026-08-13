package dev.francogomez.comercio.reportes.proyeccion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface VentaDiariaRepository extends JpaRepository<VentaDiaria, LocalDate> {

    List<VentaDiaria> findByFechaBetweenOrderByFechaDesc(LocalDate desde, LocalDate hasta);
}
