package dev.francogomez.comercio.core.precio;

import dev.francogomez.comercio.core.shared.ConflictException;
import dev.francogomez.comercio.core.shared.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ListaPrecioService {

    private final ListaPrecioRepository repository;

    public ListaPrecioService(ListaPrecioRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ListaPrecio crear(String codigo, String nombre) {
        if (repository.existsByCodigoIgnoreCase(codigo)) {
            throw new ConflictException("Ya existe una lista de precios con código '%s'".formatted(codigo));
        }
        return repository.save(new ListaPrecio(codigo, nombre));
    }

    public ListaPrecio buscar(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("No existe una lista de precios con id " + id));
    }

    public Page<ListaPrecio> listar(boolean incluirInactivas, Pageable pageable) {
        return incluirInactivas ? repository.findAll(pageable) : repository.findByActiva(true, pageable);
    }
}
