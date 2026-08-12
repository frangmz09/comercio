package dev.francogomez.comercio.core.producto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/productos")
@Tag(name = "Productos", description = "Catálogo de productos")
public class ProductoController {

    private final ProductoService service;

    public ProductoController(ProductoService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Crear un producto")
    public ResponseEntity<ProductoResponse> crear(@Valid @RequestBody ProductoRequest request) {
        Producto producto = service.crear(request);
        return ResponseEntity.created(URI.create("/api/v1/productos/" + producto.getId()))
                .body(ProductoResponse.from(producto));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar un producto por id")
    public ProductoResponse buscar(@PathVariable UUID id) {
        return ProductoResponse.from(service.buscar(id));
    }

    @GetMapping
    @Operation(summary = "Listar productos, con filtro opcional por categoría")
    public Page<ProductoResponse> listar(
            @RequestParam(required = false) String categoria,
            @RequestParam(defaultValue = "false") boolean incluirInactivos,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.listar(categoria, incluirInactivos, pageable).map(ProductoResponse::from);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar nombre, categoría y unidad de un producto")
    public ProductoResponse actualizar(@PathVariable UUID id, @Valid @RequestBody ProductoRequest request) {
        return ProductoResponse.from(service.actualizar(id, request));
    }

    @PostMapping("/{id}/desactivar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Desactivar un producto (no se elimina, queda fuera del catálogo activo)")
    public void desactivar(@PathVariable UUID id) {
        service.desactivar(id);
    }

    @PostMapping("/{id}/activar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Reactivar un producto previamente desactivado")
    public void activar(@PathVariable UUID id) {
        service.activar(id);
    }
}
