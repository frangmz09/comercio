package dev.francogomez.comercio.core.venta;

import dev.francogomez.comercio.contratos.NotaCreditoEmitida;
import dev.francogomez.comercio.contratos.VentaConfirmada;
import dev.francogomez.comercio.core.comprobante.Comprobante;
import dev.francogomez.comercio.core.comprobante.ComprobanteService;
import dev.francogomez.comercio.core.comprobante.TipoComprobante;
import dev.francogomez.comercio.core.outbox.OutboxRegistrar;
import dev.francogomez.comercio.core.precio.ListaPrecio;
import dev.francogomez.comercio.core.precio.ListaPrecioRepository;
import dev.francogomez.comercio.core.precio.Precio;
import dev.francogomez.comercio.core.precio.PrecioService;
import dev.francogomez.comercio.core.producto.Producto;
import dev.francogomez.comercio.core.producto.ProductoRepository;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class VentaService {

    private final VentaRepository ventaRepository;
    private final ClaveIdempotenciaRepository claveRepository;
    private final ListaPrecioRepository listaRepository;
    private final ProductoRepository productoRepository;
    private final PrecioService precioService;
    private final StockService stockService;
    private final ComprobanteService comprobanteService;
    private final OutboxRegistrar outbox;

    public VentaService(VentaRepository ventaRepository,
                        ClaveIdempotenciaRepository claveRepository,
                        ListaPrecioRepository listaRepository,
                        ProductoRepository productoRepository,
                        PrecioService precioService,
                        StockService stockService,
                        ComprobanteService comprobanteService,
                        OutboxRegistrar outbox) {
        this.ventaRepository = ventaRepository;
        this.claveRepository = claveRepository;
        this.listaRepository = listaRepository;
        this.productoRepository = productoRepository;
        this.precioService = precioService;
        this.stockService = stockService;
        this.comprobanteService = comprobanteService;
        this.outbox = outbox;
    }

    /**
     * Registra una venta completa en una sola transacción: resuelve el precio vigente de
     * cada producto, descuenta el stock, emite la factura y deja el evento en el outbox.
     * Si algo falla —un producto sin precio, stock insuficiente en la última línea— no
     * queda nada a medias: ni mercadería descontada, ni una venta huérfana, ni un número
     * de comprobante quemado, ni un evento anunciando algo que no ocurrió.
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

        // El stock se descuenta con el id de la venta a mano, para que cada movimiento
        // quede trazado contra el comprobante que lo originó.
        for (LineaRequest linea : lineasEnOrdenDeLock(request.lineas())) {
            stockService.registrar(linea.productoId(), TipoMovimiento.SALIDA, linea.cantidad(),
                    "venta", venta.getId().toString());
        }

        Comprobante factura = comprobanteService.emitir(
                request.puntoVentaId(), TipoComprobante.FACTURA, venta.getId(), venta.getTotal(), null);

        outbox.registrar(venta.getId(), VentaConfirmada.class.getSimpleName(),
                eventoDe(venta, factura));

        registrarClave(claveIdempotencia, huella, venta);
        return venta;
    }

    /**
     * Emite la nota de crédito que revierte una venta: devuelve la mercadería al stock,
     * deja el comprobante apuntando al original y marca la venta como anulada. Nada se
     * borra ni se edita — la factura original sigue existiendo tal cual se emitió.
     */
    @Transactional
    public Comprobante emitirNotaCredito(UUID ventaId, UUID puntoVentaId, String motivo) {
        Venta venta = buscar(ventaId);
        if (venta.getEstado() == EstadoVenta.ANULADA) {
            throw new ConflictException("La venta " + ventaId + " ya está anulada");
        }

        Comprobante original = comprobanteService.facturaDe(ventaId);

        for (LineaVenta linea : venta.getLineas()) {
            stockService.registrar(linea.getProductoId(), TipoMovimiento.ENTRADA, linea.getCantidad(),
                    motivo != null ? motivo : "nota de crédito", ventaId.toString());
        }

        venta.anular();

        Comprobante notaCredito = comprobanteService.emitir(
                puntoVentaId, TipoComprobante.NOTA_CREDITO, ventaId, venta.getTotal(), original.getId());

        outbox.registrar(ventaId, NotaCreditoEmitida.class.getSimpleName(),
                new NotaCreditoEmitida(
                        VentaConfirmada.CURRENT_SCHEMA_VERSION,
                        UUID.randomUUID(),
                        notaCredito.getId(),
                        ventaId,
                        notaCredito.getCreadoEn(),
                        notaCredito.getTotal()));

        return notaCredito;
    }

    public Venta buscar(UUID id) {
        return ventaRepository.findWithLineasById(id)
                .orElseThrow(() -> new NotFoundException("No existe una venta con id " + id));
    }

    public Page<Venta> listar(Pageable pageable) {
        return ventaRepository.findAllBy(pageable);
    }

    public List<Comprobante> comprobantesDe(UUID ventaId) {
        return comprobanteService.deVenta(ventaId);
    }

    /**
     * Arma el evento con los SKU y no con los ids internos: quien lo consume vive en otro
     * servicio, con su propia base, y un UUID de la tabla producto de este no le dice nada.
     */
    private VentaConfirmada eventoDe(Venta venta, Comprobante factura) {
        List<UUID> ids = venta.getLineas().stream().map(LineaVenta::getProductoId).toList();
        Map<UUID, String> skus = productoRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Producto::getId, Producto::getSku));

        List<VentaConfirmada.Linea> lineas = venta.getLineas().stream()
                .map(l -> new VentaConfirmada.Linea(
                        skus.getOrDefault(l.getProductoId(), l.getProductoId().toString()),
                        l.getCantidad(),
                        l.getPrecioUnitario()))
                .toList();

        return new VentaConfirmada(
                VentaConfirmada.CURRENT_SCHEMA_VERSION,
                UUID.randomUUID(),
                venta.getId(),
                factura.getNumeroFormateado(),
                venta.getCreadoEn(),
                lineas,
                venta.getTotal());
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
        String canonico = request.listaPrecioId() + "|" + request.puntoVentaId() + "|"
                + request.lineas().stream()
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
