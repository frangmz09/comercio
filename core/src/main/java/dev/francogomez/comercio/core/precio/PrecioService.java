package dev.francogomez.comercio.core.precio;

import dev.francogomez.comercio.core.producto.Producto;
import dev.francogomez.comercio.core.producto.ProductoRepository;
import dev.francogomez.comercio.core.shared.ConflictException;
import dev.francogomez.comercio.core.shared.NotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PrecioService {

    /** Nombre de la constraint de exclusión definida en la migración V2. */
    private static final String CONSTRAINT_SOLAPAMIENTO = "ex_precio_sin_solapamiento";

    private final PrecioRepository precioRepository;
    private final ListaPrecioRepository listaRepository;
    private final ProductoRepository productoRepository;

    public PrecioService(PrecioRepository precioRepository,
                         ListaPrecioRepository listaRepository,
                         ProductoRepository productoRepository) {
        this.precioRepository = precioRepository;
        this.listaRepository = listaRepository;
        this.productoRepository = productoRepository;
    }

    /**
     * Da de alta un precio cerrando automáticamente el que estaba vigente. Es la única
     * forma de "cambiar" un precio: el anterior no se pisa, queda cerrado y consultable,
     * porque las ventas históricas necesitan poder explicar a qué precio se vendieron.
     */
    @Transactional
    public Precio asignar(UUID productoId, UUID listaId, BigDecimal monto, Instant vigenciaDesde) {
        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new NotFoundException("No existe un producto con id " + productoId));
        ListaPrecio lista = listaRepository.findById(listaId)
                .orElseThrow(() -> new NotFoundException("No existe una lista de precios con id " + listaId));

        precioRepository.findVigenteParaActualizar(productoId, listaId, vigenciaDesde)
                .ifPresent(actual -> {
                    cerrar(actual, vigenciaDesde);
                    // Hibernate ordena su cola de acciones con los INSERT antes que los
                    // UPDATE, sin importar en qué orden se escribió el código. Sin este
                    // flush, el precio nuevo se inserta mientras el anterior todavía
                    // tiene vigencia_hasta NULL: los rangos se solapan y la constraint
                    // de exclusión rechaza la operación entera.
                    precioRepository.flush();
                });

        Precio nuevo = new Precio(producto, lista, monto, vigenciaDesde);
        try {
            // El flush explícito trae el error de la constraint acá adentro, donde se puede
            // traducir a un 409 con sentido, en vez de que estalle al cerrar la transacción.
            return precioRepository.saveAndFlush(nuevo);
        } catch (DataIntegrityViolationException ex) {
            throw traducirSiEsSolapamiento(ex, productoId, listaId);
        }
    }

    public Precio vigente(UUID productoId, UUID listaId, Instant momento) {
        return precioRepository.findVigente(productoId, listaId, momento)
                .orElseThrow(() -> new NotFoundException(
                        "El producto %s no tiene precio vigente en la lista %s al %s"
                                .formatted(productoId, listaId, momento)));
    }

    public List<Precio> historial(UUID productoId, UUID listaId) {
        return precioRepository.findHistorial(productoId, listaId);
    }

    private void cerrar(Precio actual, Instant vigenciaDesde) {
        if (!vigenciaDesde.isAfter(actual.getVigenciaDesde())) {
            throw new ConflictException(
                    "Ya hay un precio que arranca el %s; el nuevo debe empezar después"
                            .formatted(actual.getVigenciaDesde()));
        }
        actual.cerrarEn(vigenciaDesde);
    }

    private RuntimeException traducirSiEsSolapamiento(DataIntegrityViolationException ex,
                                                      UUID productoId, UUID listaId) {
        String causa = ex.getMostSpecificCause().getMessage();
        if (causa != null && causa.contains(CONSTRAINT_SOLAPAMIENTO)) {
            return new ConflictException(
                    "La vigencia se solapa con un precio ya cargado para el producto %s en la lista %s"
                            .formatted(productoId, listaId));
        }
        return ex;
    }
}
