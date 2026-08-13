package dev.francogomez.comercio.core.stock;

import dev.francogomez.comercio.core.stock.StockDtos.MovimientoRequest;
import dev.francogomez.comercio.core.stock.StockDtos.MovimientoResponse;
import dev.francogomez.comercio.core.stock.StockDtos.SaldoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stock")
@Tag(name = "Stock", description = "Movimientos de stock y saldos")
public class StockController {

    private final StockService service;

    public StockController(StockService service) {
        this.service = service;
    }

    @PostMapping("/movimientos")
    @Operation(summary = "Registrar un movimiento de stock (entrada, salida o ajuste)")
    public ResponseEntity<MovimientoResponse> registrar(@Valid @RequestBody MovimientoRequest request) {
        MovimientoStock movimiento = service.registrar(
                request.productoId(), request.tipo(), request.cantidad(),
                request.motivo(), request.referencia());
        return ResponseEntity.status(HttpStatus.CREATED).body(MovimientoResponse.from(movimiento));
    }

    @GetMapping("/{productoId}")
    @Operation(summary = "Saldo actual de un producto")
    public SaldoResponse saldo(@PathVariable UUID productoId) {
        return new SaldoResponse(productoId, service.saldoDe(productoId));
    }

    @GetMapping("/{productoId}/movimientos")
    @Operation(summary = "Historial de movimientos de un producto, del más reciente al más viejo")
    public Page<MovimientoResponse> movimientos(@PathVariable UUID productoId,
                                                @PageableDefault(size = 20) Pageable pageable) {
        return service.movimientosDe(productoId, pageable).map(MovimientoResponse::from);
    }
}
