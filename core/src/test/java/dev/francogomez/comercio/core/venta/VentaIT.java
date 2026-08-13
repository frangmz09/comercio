package dev.francogomez.comercio.core.venta;

import dev.francogomez.comercio.core.comprobante.ComprobanteService;
import dev.francogomez.comercio.core.precio.ListaPrecioService;
import dev.francogomez.comercio.core.precio.PrecioService;
import dev.francogomez.comercio.core.producto.Producto;
import dev.francogomez.comercio.core.producto.ProductoRepository;
import dev.francogomez.comercio.core.shared.ConflictException;
import dev.francogomez.comercio.core.stock.StockService;
import dev.francogomez.comercio.core.stock.TipoMovimiento;
import dev.francogomez.comercio.core.venta.VentaDtos.LineaRequest;
import dev.francogomez.comercio.core.venta.VentaDtos.VentaRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
class VentaIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private VentaService ventaService;

    @Autowired
    private StockService stockService;

    @Autowired
    private PrecioService precioService;

    @Autowired
    private ListaPrecioService listaService;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private ComprobanteService comprobanteService;

    private UUID listaId;
    private UUID puntoVentaId;

    @BeforeEach
    void prepararLista() {
        listaId = listaService.crear("L-" + UUID.randomUUID().toString().substring(0, 8), "Minorista").getId();
        // Numero aleatorio: cada test necesita su propio punto de venta para que las
        // secuencias de comprobantes no se mezclen entre casos.
        puntoVentaId = comprobanteService.crearPuntoVenta(
                ThreadLocalRandom.current().nextInt(1, 1_000_000), "Caja de prueba").getId();
    }

    @Test
    void unaVentaDescuentaStockYCongelaElPrecioDelMomento() {
        UUID producto = productoConPrecioYStock("100.00", 10);

        Venta venta = ventaService.registrar(
                new VentaRequest(listaId, puntoVentaId, List.of(new LineaRequest(producto, new BigDecimal("3")))),
                clave());

        assertThat(venta.getTotal()).isEqualByComparingTo("300.00");
        assertThat(venta.getEstado()).isEqualTo(EstadoVenta.CONFIRMADA);
        assertThat(stockService.saldoDe(producto)).isEqualByComparingTo("7");

        LineaVenta linea = venta.getLineas().getFirst();
        assertThat(linea.getPrecioUnitario()).isEqualByComparingTo("100.00");
        assertThat(linea.getSubtotal()).isEqualByComparingTo("300.00");
    }

    @Test
    void subirElPrecioDespuesDeVenderNoCambiaLaVentaYaRegistrada() {
        UUID producto = productoConPrecioYStock("100.00", 10);

        Venta venta = ventaService.registrar(
                new VentaRequest(listaId, puntoVentaId, List.of(new LineaRequest(producto, new BigDecimal("2")))),
                clave());

        precioService.asignar(producto, listaId, new BigDecimal("250.00"), Instant.now());

        // El precio del catálogo cambió, pero la venta guarda el suyo propio.
        assertThat(ventaService.buscar(venta.getId()).getTotal()).isEqualByComparingTo("200.00");
        assertThat(precioService.vigente(producto, listaId, Instant.now()).getMonto())
                .isEqualByComparingTo("250.00");
    }

    @Test
    void elTotalSumaTodasLasLineas() {
        UUID uno = productoConPrecioYStock("100.50", 10);
        UUID otro = productoConPrecioYStock("49.50", 10);

        Venta venta = ventaService.registrar(
                new VentaRequest(listaId, puntoVentaId, List.of(
                        new LineaRequest(uno, new BigDecimal("2")),
                        new LineaRequest(otro, new BigDecimal("1")))),
                clave());

        assertThat(venta.getTotal()).isEqualByComparingTo("250.50");
        assertThat(venta.getLineas()).hasSize(2);
    }

    @Test
    void siUnaLineaNoTieneStockNoSeRegistraNadaDeLaVenta() {
        UUID conStock = productoConPrecioYStock("100.00", 10);
        UUID sinStock = productoConPrecioYStock("100.00", 1);

        assertThatThrownBy(() -> ventaService.registrar(
                new VentaRequest(listaId, puntoVentaId, List.of(
                        new LineaRequest(conStock, new BigDecimal("2")),
                        new LineaRequest(sinStock, new BigDecimal("5")))),
                clave()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Stock insuficiente");

        // Lo que prueba la atomicidad: la primera línea no descontó nada pese a que sí
        // había stock para ella.
        assertThat(stockService.saldoDe(conStock)).isEqualByComparingTo("10");
        assertThat(stockService.saldoDe(sinStock)).isEqualByComparingTo("1");
    }

    @Test
    void venderUnProductoSinPrecioEnLaListaFalla() {
        Producto sinPrecio = productoRepository.save(
                new Producto("SP-" + UUID.randomUUID().toString().substring(0, 8), "Sin precio", "Test", "unidad"));
        stockService.registrar(sinPrecio.getId(), TipoMovimiento.ENTRADA, new BigDecimal("5"), "carga", null);

        assertThatThrownBy(() -> ventaService.registrar(
                new VentaRequest(listaId, puntoVentaId, List.of(new LineaRequest(sinPrecio.getId(), BigDecimal.ONE))),
                clave()))
                .hasMessageContaining("no tiene precio vigente");

        assertThat(stockService.saldoDe(sinPrecio.getId())).isEqualByComparingTo("5");
    }

    @Test
    void repetirLaClaveDeIdempotenciaDevuelveLaMismaVentaSinVolverADescontar() {
        UUID producto = productoConPrecioYStock("100.00", 10);
        String clave = clave();
        VentaRequest pedido = new VentaRequest(listaId, puntoVentaId, List.of(new LineaRequest(producto, new BigDecimal("3"))));

        Venta primera = ventaService.registrar(pedido, clave);
        Venta segunda = ventaService.registrar(pedido, clave);

        assertThat(segunda.getId()).isEqualTo(primera.getId());
        // Lo importante: el stock se descontó una sola vez.
        assertThat(stockService.saldoDe(producto)).isEqualByComparingTo("7");
    }

    @Test
    void reusarLaClaveConOtroContenidoSeRechaza() {
        UUID producto = productoConPrecioYStock("100.00", 10);
        String clave = clave();

        ventaService.registrar(
                new VentaRequest(listaId, puntoVentaId, List.of(new LineaRequest(producto, new BigDecimal("3")))), clave);

        assertThatThrownBy(() -> ventaService.registrar(
                new VentaRequest(listaId, puntoVentaId, List.of(new LineaRequest(producto, new BigDecimal("5")))), clave))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ya se usó para una venta distinta");
    }

    @Test
    void elMismoProductoDosVecesEnUnaVentaSeRechaza() {
        UUID producto = productoConPrecioYStock("100.00", 10);

        assertThatThrownBy(() -> ventaService.registrar(
                new VentaRequest(listaId, puntoVentaId, List.of(
                        new LineaRequest(producto, BigDecimal.ONE),
                        new LineaRequest(producto, new BigDecimal("2")))),
                clave()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no puede aparecer en dos líneas");
    }

    @Test
    void ochoReintentosSimultaneosConLaMismaClaveRegistranUnaSolaVenta() throws Exception {
        int hilos = 8;
        UUID producto = productoConPrecioYStock("100.00", 50);
        String clave = clave();
        VentaRequest pedido = new VentaRequest(listaId, puntoVentaId, List.of(new LineaRequest(producto, new BigDecimal("2"))));

        CountDownLatch largada = new CountDownLatch(1);
        AtomicInteger exitos = new AtomicInteger();
        AtomicReference<UUID> ventaId = new AtomicReference<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(hilos)) {
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < hilos; i++) {
                futures.add(pool.submit(() -> {
                    largada.await();
                    try {
                        Venta v = ventaService.registrar(pedido, clave);
                        exitos.incrementAndGet();
                        ventaId.compareAndSet(null, v.getId());
                    } catch (ConflictException esperado) {
                        // Reintentos que llegaron demasiado juntos: la PK de
                        // clave_idempotencia los frenó.
                    }
                    return null;
                }));
            }
            largada.countDown();
            for (Future<?> f : futures) {
                f.get();
            }
        }

        assertThat(exitos.get()).isPositive();
        // La garantía que importa: por más reintentos que lleguen, se descontaron 2
        // unidades una sola vez.
        assertThat(stockService.saldoDe(producto)).isEqualByComparingTo("48");
        assertThat(ventaService.buscar(ventaId.get()).getTotal()).isEqualByComparingTo("200.00");
    }

    private UUID productoConPrecioYStock(String precio, int unidades) {
        Producto producto = productoRepository.save(
                new Producto("VTA-" + UUID.randomUUID().toString().substring(0, 8), "Producto", "Test", "unidad"));
        precioService.asignar(producto.getId(), listaId, new BigDecimal(precio), Instant.now());
        stockService.registrar(producto.getId(), TipoMovimiento.ENTRADA,
                BigDecimal.valueOf(unidades), "carga inicial", null);
        return producto.getId();
    }

    private String clave() {
        return "test-" + UUID.randomUUID();
    }
}
