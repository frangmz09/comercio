package dev.francogomez.comercio.core.venta;

import dev.francogomez.comercio.core.comprobante.Comprobante;
import dev.francogomez.comercio.core.venta.VentaDtos.ComprobanteResponse;
import dev.francogomez.comercio.core.venta.VentaDtos.NotaCreditoRequest;
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
import org.springframework.http.HttpStatus;
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
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ventas")
@Tag(name = "Ventas", description = "Registro de ventas y notas de crédito")
@Validated
public class VentaController {

    private final VentaService service;

    public VentaController(VentaService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Registrar una venta",
            description = """
                    Resuelve el precio vigente de cada producto, descuenta el stock, emite la
                    factura y publica el evento, todo en una sola transacción. Requiere el
                    header `Idempotency-Key`: si el mismo pedido llega dos veces con la misma
                    clave, la segunda devuelve la venta ya registrada en lugar de cobrar de
                    nuevo.""")
    public ResponseEntity<VentaResponse> registrar(
            @Parameter(description = "Clave única generada por el cliente para poder reintentar sin duplicar",
                    required = true, example = "caja-3-20260813-000417")
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 120) String idempotencyKey,
            @Valid @RequestBody VentaRequest request) {

        Venta venta = service.registrar(request, idempotencyKey);
        return ResponseEntity.created(URI.create("/api/v1/ventas/" + venta.getId()))
                .body(VentaResponse.from(venta, service.comprobantesDe(venta.getId())));
    }

    @PostMapping("/{id}/nota-credito")
    @Operation(summary = "Emitir una nota de crédito que revierte la venta",
            description = """
                    Devuelve la mercadería al stock y deja el comprobante apuntando a la factura
                    original. No borra ni modifica nada de la venta: la factura sigue existiendo
                    tal como se emitió.""")
    public ResponseEntity<ComprobanteResponse> emitirNotaCredito(
            @PathVariable UUID id,
            @Valid @RequestBody NotaCreditoRequest request) {

        Comprobante nota = service.emitirNotaCredito(id, request.puntoVentaId(), request.motivo());
        return ResponseEntity.status(HttpStatus.CREATED).body(ComprobanteResponse.from(nota));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar una venta por id, con sus comprobantes")
    public VentaResponse buscar(@PathVariable UUID id) {
        return VentaResponse.from(service.buscar(id), service.comprobantesDe(id));
    }

    @GetMapping("/{id}/comprobantes")
    @Operation(summary = "Comprobantes emitidos para una venta")
    public List<ComprobanteResponse> comprobantes(@PathVariable UUID id) {
        return service.comprobantesDe(id).stream().map(ComprobanteResponse::from).toList();
    }

    @GetMapping
    @Operation(summary = "Listar ventas")
    public Page<VentaResponse> listar(@PageableDefault(size = 20) Pageable pageable) {
        return service.listar(pageable).map(v -> VentaResponse.from(v, List.of()));
    }
}
