package dev.francogomez.comercio.core.venta;

import dev.francogomez.comercio.core.venta.VentaDtos.VentaRequest;
import dev.francogomez.comercio.core.venta.VentaDtos.VentaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ventas")
@Tag(name = "Ventas", description = "Registro de ventas")
@Validated
public class VentaController {

    private final VentaService service;

    public VentaController(VentaService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Registrar una venta",
            description = """
                    Resuelve el precio vigente de cada producto, descuenta el stock y calcula el
                    total, todo en una sola transacción. Requiere el header `Idempotency-Key`:
                    si el mismo pedido llega dos veces con la misma clave, la segunda devuelve
                    la venta ya registrada en lugar de cobrar de nuevo.""")
    public ResponseEntity<VentaResponse> registrar(
            @Parameter(description = "Clave única generada por el cliente para poder reintentar sin duplicar",
                    required = true, example = "caja-3-20260813-000417")
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 120) String idempotencyKey,
            @Valid @RequestBody VentaRequest request) {

        Venta venta = service.registrar(request, idempotencyKey);
        return ResponseEntity.created(URI.create("/api/v1/ventas/" + venta.getId()))
                .body(VentaResponse.from(venta));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar una venta por id")
    public VentaResponse buscar(@PathVariable UUID id) {
        return VentaResponse.from(service.buscar(id));
    }

    @GetMapping
    @Operation(summary = "Listar ventas")
    public Page<VentaResponse> listar(@PageableDefault(size = 20) Pageable pageable) {
        return service.listar(pageable).map(VentaResponse::from);
    }
}
