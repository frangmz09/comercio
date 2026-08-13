package dev.francogomez.comercio.core.stock;

import dev.francogomez.comercio.core.producto.Producto;
import dev.francogomez.comercio.core.producto.ProductoRepository;
import dev.francogomez.comercio.core.shared.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class StockService {

    private final SaldoStockRepository saldoRepository;
    private final MovimientoStockRepository movimientoRepository;
    private final ProductoRepository productoRepository;

    public StockService(SaldoStockRepository saldoRepository,
                        MovimientoStockRepository movimientoRepository,
                        ProductoRepository productoRepository) {
        this.saldoRepository = saldoRepository;
        this.movimientoRepository = movimientoRepository;
        this.productoRepository = productoRepository;
    }

    /**
     * Registra un movimiento y actualiza el saldo en la misma transacción. El orden
     * importa: primero se toma el lock sobre el saldo y recién después se valida y
     * escribe. Validar antes de bloquear sería leer un valor que puede cambiar
     * inmediatamente después, que es exactamente cómo se sobrevende.
     */
    @Transactional
    public MovimientoStock registrar(UUID productoId, TipoMovimiento tipo, BigDecimal cantidad,
                                     String motivo, String referencia) {
        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new NotFoundException("No existe un producto con id " + productoId));

        SaldoStock saldo = bloquearSaldo(productoId);

        BigDecimal conSigno = tipo.aplicarSigno(cantidad);
        saldo.aplicar(conSigno);

        return movimientoRepository.save(new MovimientoStock(producto, tipo, conSigno, motivo, referencia));
    }

    public BigDecimal saldoDe(UUID productoId) {
        if (!productoRepository.existsById(productoId)) {
            throw new NotFoundException("No existe un producto con id " + productoId);
        }
        return saldoRepository.findById(productoId)
                .map(SaldoStock::getCantidad)
                .orElse(BigDecimal.ZERO);
    }

    public Page<MovimientoStock> movimientosDe(UUID productoId, Pageable pageable) {
        if (!productoRepository.existsById(productoId)) {
            throw new NotFoundException("No existe un producto con id " + productoId);
        }
        return movimientoRepository.findByProductoIdOrderByCreadoEnDesc(productoId, pageable);
    }

    private SaldoStock bloquearSaldo(UUID productoId) {
        // Asegura que la fila exista antes de intentar bloquearla: no se puede tomar un
        // FOR UPDATE sobre algo que todavía no está.
        saldoRepository.crearSiNoExiste(productoId);
        return saldoRepository.findParaActualizar(productoId)
                .orElseThrow(() -> new IllegalStateException(
                        "El saldo del producto " + productoId + " debería existir tras crearSiNoExiste"));
    }
}
