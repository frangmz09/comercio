package dev.francogomez.comercio.core.producto;

import dev.francogomez.comercio.core.shared.ConflictException;
import dev.francogomez.comercio.core.shared.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProductoService {

    private final ProductoRepository repository;

    public ProductoService(ProductoRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Producto crear(ProductoRequest request) {
        if (repository.existsBySku(request.sku())) {
            throw new ConflictException("Ya existe un producto con SKU '%s'".formatted(request.sku()));
        }
        Producto producto = new Producto(request.sku(), request.nombre(), request.categoria(), request.unidad());
        return repository.save(producto);
    }

    public Producto buscar(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("No existe un producto con id " + id));
    }

    public Page<Producto> listar(String categoria, boolean incluirInactivos, Pageable pageable) {
        boolean hayCategoria = categoria != null && !categoria.isBlank();
        if (hayCategoria && incluirInactivos) {
            return repository.findByCategoriaIgnoreCase(categoria, pageable);
        }
        if (hayCategoria) {
            return repository.findByCategoriaIgnoreCaseAndActivo(categoria, true, pageable);
        }
        if (incluirInactivos) {
            return repository.findAll(pageable);
        }
        return repository.findByActivo(true, pageable);
    }

    @Transactional
    public Producto actualizar(UUID id, ProductoRequest request) {
        Producto producto = buscar(id);
        repository.findBySku(request.sku())
                .filter(existente -> !id.equals(existente.getId()))
                .ifPresent(existente -> {
                    throw new ConflictException("Ya existe un producto con SKU '%s'".formatted(request.sku()));
                });
        producto.actualizar(request.nombre(), request.categoria(), request.unidad());
        return producto;
    }

    @Transactional
    public void desactivar(UUID id) {
        buscar(id).desactivar();
    }

    @Transactional
    public void activar(UUID id) {
        buscar(id).activar();
    }
}
