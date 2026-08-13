package dev.francogomez.comercio.core.comprobante;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Comprobantes", description = "Puntos de venta y comprobantes emitidos")
public class ComprobanteController {

    private final ComprobanteService service;

    public ComprobanteController(ComprobanteService service) {
        this.service = service;
    }

    @PostMapping("/puntos-venta")
    @Operation(summary = "Crear un punto de venta",
            description = "Cada punto de venta numera sus comprobantes por separado.")
    public ResponseEntity<PuntoVentaResponse> crearPuntoVenta(@Valid @RequestBody PuntoVentaRequest request) {
        PuntoVenta pv = service.crearPuntoVenta(request.numero(), request.nombre());
        return ResponseEntity.created(URI.create("/api/v1/puntos-venta/" + pv.getId()))
                .body(PuntoVentaResponse.from(pv));
    }

    @GetMapping("/comprobantes/{id}")
    @Operation(summary = "Buscar un comprobante por id")
    public ComprobanteDetalle buscar(@PathVariable UUID id) {
        return ComprobanteDetalle.from(service.buscar(id));
    }

    public record PuntoVentaRequest(
            @NotNull @Min(value = 1, message = "el número debe ser positivo") Integer numero,
            @NotBlank @Size(max = 100) String nombre) {
    }

    public record PuntoVentaResponse(UUID id, int numero, String nombre, boolean activo) {

        static PuntoVentaResponse from(PuntoVenta pv) {
            return new PuntoVentaResponse(pv.getId(), pv.getNumero(), pv.getNombre(), pv.isActivo());
        }
    }

    public record ComprobanteDetalle(
            UUID id,
            TipoComprobante tipo,
            String numero,
            UUID ventaId,
            UUID revierteA,
            BigDecimal total,
            Instant creadoEn) {

        static ComprobanteDetalle from(Comprobante c) {
            return new ComprobanteDetalle(
                    c.getId(), c.getTipo(), c.getNumeroFormateado(), c.getVentaId(),
                    c.getRevierteA(), c.getTotal(), c.getCreadoEn());
        }
    }
}
