package dev.francogomez.comercio.core.venta;

import dev.francogomez.comercio.core.precio.ListaPrecio;
import dev.francogomez.comercio.core.precio.ListaPrecioRepository;
import dev.francogomez.comercio.core.precio.Precio;
import dev.francogomez.comercio.core.precio.PrecioService;
import dev.francogomez.comercio.core.shared.ConflictException;
import dev.francogomez.comercio.core.shared.NotFoundException;
import dev.francogomez.comercio.core.stock.StockService;
import dev.francogomez.comercio.core.stock.TipoMovimiento;
import dev.francogomez.comercio.core.venta.VentaDtos.LineaRequest;
import dev.francogomez.comercio.core.venta.VentaDtos.VentaRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class VentaService {

    private final VentaRepository ventaRepository;
    private final ClaveIdempotenciaRepository claveRepository;
    private final ListaPrecioRepository listaRepository;
    private final PrecioService precioService;
    private final StockService stockService;

    public VentaService(VentaRepository ventaRepository,
                        ClaveIdempotenciaRepository claveRepository,
                        ListaPrecioRepository listaRepository,
                        PrecioService precioService,
                        StockService stockService) {
        this.ventaRepository = ventaRepository;
        this.claveRepository = claveRepository;
        this.listaRepository = listaRepository;
        this.precioService = precioService;
        this.stockService = stockService;
    }

    /**
     * Registra una venta completa en una sola transacción: resuelve el precio vigente de
     * cada producto, descuenta el stock y calcula el total. Si algo falla —un producto
     * sin precio, stock insuficiente en la última línea— no queda nada a medias: ni
     * mercadería descontada ni una venta huérfana.
     */
    @Transactional
    public Venta registrar(VentaRequest request, String claveIdempotencia) {
        String huella = huellaDe(request);

        Optional<Venta> yaRegistrada = recuperarPorClave(claveIdempotencia, huella);
        if (yaRegistrada.isPresent()) {
            return yaRegistrada.get();
        }

        rechazarProductosRepetidos(request.lineas());

        ListaPrecio lista = listaRepository.findById(request.listaPrecioId())
                .orElseThrow(() -> new NotFoundException(
                        "No existe una lista de precios con id " + request.listaPrecioId()));

        Venta venta = new Venta(lista);
        Instant momento = venta.getCreadoEn();

        for (LineaRequest linea : lineasEnOrdenDeLock(request.lineas())) {
            Precio precio = precioService.vigente(linea.productoId(), lista.getId(), momento);
            venta.agregarLinea(linea.productoId(), linea.cantidad(), precio.getMonto());
        }

        ventaRepository.save(venta);

        // El stock se descuenta después de tener el id de la venta, para que cada
        // movimiento quede trazado contra el comprobante que lo originó.
        for (LineaRequest linea : lineasEnOrdenDeLock(request.lineas())) {
            stockService.registrar(linea.productoId(), TipoMovimiento.SALIDA, linea.cantidad(),
                    "venta", venta.getId().toString());
        }

        registrarClave(claveIdempotencia, huella, venta);
        return venta;
    }

    public Venta buscar(UUID id) {
        return ventaRepository.findWithLineasById(id)
                .orElseThrow(() -> new NotFoundException("No existe una venta con id " + id));
    }

    public Page<Venta> listar(Pageable pageable) {
        return ventaRepository.findAllBy(pageable);
    }

    /**
     * Si la clave ya fue usada, devuelve la venta original en lugar de registrar otra.
     * Es el caso normal: el punto de venta no recibió la respuesta y reintentó.
     */
    private Optional<Venta> recuperarPorClave(String clave, String huella) {
        return claveRepository.findById(clave).map(registrada -> {
            if (!registrada.coincideCon(huella)) {
                throw new ConflictException(
                        "La clave de idempotencia '%s' ya se usó para una venta distinta".formatted(clave));
            }
            return buscar(registrada.getVentaId());
        });
    }

    private void registrarClave(String clave, String huella, Venta venta) {
        try {
            // Ocurre cuando dos reintentos llegan tan juntos que ninguno vio al otro en el
            // chequeo previo: la PK de la tabla es lo que finalmente garantiza que solo
            // una venta quede registrada. Ver el comentario del repositorio sobre por qué
            // esto es un INSERT explícito y no un save().
            claveRepository.insertar(clave, huella, venta.getId());
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException(
                    "Ya hay una venta en curso con la clave de idempotencia '%s'; reintentá en unos instantes"
                            .formatted(clave));
        }
    }

    private void rechazarProductosRepetidos(List<LineaRequest> lineas) {
        long distintos = lineas.stream().map(LineaRequest::productoId).distinct().count();
        if (distintos != lineas.size()) {
            throw new ConflictException(
                    "Un producto no puede aparecer en dos líneas de la misma venta: acumulá la cantidad");
        }
    }

    /**
     * Ordena las líneas por id de producto antes de tocar el stock. Dos ventas que
     * incluyen los mismos productos en distinto orden tomarían los locks en distinto
     * orden y podrían quedar trabadas mutuamente. Con un orden total y estable, la
     * segunda espera a la primera en lugar de abrazarse con ella.
     */
    private List<LineaRequest> lineasEnOrdenDeLock(List<LineaRequest> lineas) {
        return lineas.stream()
                .sorted(Comparator.comparing(LineaRequest::productoId))
                .toList();
    }

    /**
     * Huella del pedido, para distinguir un reintento legítimo de una clave reusada con
     * otro contenido. Se normaliza el orden de las líneas para que dos envíos iguales
     * pero desordenados den la misma huella.
     */
    private String huellaDe(VentaRequest request) {
        String canonico = request.listaPrecioId() + "|" + request.lineas().stream()
                .sorted(Comparator.comparing(LineaRequest::productoId))
                .map(l -> l.productoId() + ":" + l.cantidad().stripTrailingZeros().toPlainString())
                .collect(Collectors.joining(","));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonico.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 debería estar disponible en cualquier JRE", e);
        }
    }

}
