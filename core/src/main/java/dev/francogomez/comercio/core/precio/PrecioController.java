package dev.francogomez.comercio.core.precio;

import dev.francogomez.comercio.core.precio.PrecioDtos.ListaPrecioRequest;
import dev.francogomez.comercio.core.precio.PrecioDtos.ListaPrecioResponse;
import dev.francogomez.comercio.core.precio.PrecioDtos.PrecioRequest;
import dev.francogomez.comercio.core.precio.PrecioDtos.PrecioResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/listas-precio")
@Tag(name = "Precios", description = "Listas de precios y vigencias")
public class PrecioController {

    private final ListaPrecioService listaService;
    private final PrecioService precioService;

    public PrecioController(ListaPrecioService listaService, PrecioService precioService) {
        this.listaService = listaService;
        this.precioService = precioService;
    }

    @PostMapping
    @Operation(summary = "Crear una lista de precios")
    @ApiResponse(responseCode = "201", description = "Lista creada")
    @ApiResponse(responseCode = "409", description = "Ya existe una lista con ese código")
    public ResponseEntity<ListaPrecioResponse> crearLista(@Valid @RequestBody ListaPrecioRequest request) {
        ListaPrecio lista = listaService.crear(request.codigo(), request.nombre());
        return ResponseEntity.created(URI.create("/api/v1/listas-precio/" + lista.getId()))
                .body(ListaPrecioResponse.from(lista));
    }

    @GetMapping
    @Operation(summary = "Listar listas de precios")
    @ApiResponse(responseCode = "200", description = "Las listas de precios, paginadas")
    public Page<ListaPrecioResponse> listarListas(
            @RequestParam(defaultValue = "false") boolean incluirInactivas,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return listaService.listar(incluirInactivas, pageable).map(ListaPrecioResponse::from);
    }

    @GetMapping("/{listaId}")
    @Operation(summary = "Buscar una lista de precios por id")
    @ApiResponse(responseCode = "200", description = "La lista pedida")
    @ApiResponse(responseCode = "404", description = "No existe una lista con ese id")
    public ListaPrecioResponse buscarLista(@PathVariable UUID listaId) {
        return ListaPrecioResponse.from(listaService.buscar(listaId));
    }

    @PostMapping("/{listaId}/precios")
    @Operation(summary = "Asignar un precio, cerrando la vigencia del anterior")
    @ApiResponse(responseCode = "201", description = "Precio asignado y vigencia anterior cerrada")
    @ApiResponse(responseCode = "404", description = "No existe el producto o la lista")
    @ApiResponse(responseCode = "409",
            description = "La vigencia se solapa con un precio ya cargado para ese producto")
    public ResponseEntity<PrecioResponse> asignarPrecio(@PathVariable UUID listaId,
                                                        @Valid @RequestBody PrecioRequest request) {
        Instant desde = request.vigenciaDesde() != null ? request.vigenciaDesde() : Instant.now();
        Precio precio = precioService.asignar(request.productoId(), listaId, request.monto(), desde);
        return ResponseEntity.created(URI.create("/api/v1/listas-precio/" + listaId + "/precios"))
                .body(PrecioResponse.from(precio));
    }

    @GetMapping("/{listaId}/precios/vigente")
    @Operation(summary = "Precio vigente de un producto en una fecha (por defecto, ahora)")
    @ApiResponse(responseCode = "200", description = "El precio que regía en ese momento")
    @ApiResponse(responseCode = "404",
            description = "No existe el producto o la lista, o no había precio vigente en esa fecha")
    public PrecioResponse precioVigente(@PathVariable UUID listaId,
                                        @RequestParam UUID productoId,
                                        @RequestParam(required = false) Instant momento) {
        Instant cuando = momento != null ? momento : Instant.now();
        return PrecioResponse.from(precioService.vigente(productoId, listaId, cuando));
    }

    @GetMapping("/{listaId}/precios/historial")
    @Operation(summary = "Historial completo de precios de un producto en la lista")
    @ApiResponse(responseCode = "200", description = "Todos los precios del producto, vigentes y cerrados")
    @ApiResponse(responseCode = "404", description = "No existe el producto o la lista")
    public List<PrecioResponse> historial(@PathVariable UUID listaId, @RequestParam UUID productoId) {
        return precioService.historial(productoId, listaId).stream().map(PrecioResponse::from).toList();
    }
}
