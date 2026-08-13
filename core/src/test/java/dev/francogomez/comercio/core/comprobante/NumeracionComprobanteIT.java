package dev.francogomez.comercio.core.comprobante;

import dev.francogomez.comercio.core.precio.ListaPrecioRepository;
import dev.francogomez.comercio.core.precio.ListaPrecioService;
import dev.francogomez.comercio.core.venta.Venta;
import dev.francogomez.comercio.core.venta.VentaRepository;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La numeración de comprobantes es el problema de concurrencia más visible del dominio:
 * un hueco o un número repetido no son un detalle técnico, son algo que después hay que
 * justificar ante quien audite los comprobantes.
 */
@Testcontainers
@SpringBootTest
class NumeracionComprobanteIT {

    private static final int HILOS = 16;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private ComprobanteService service;

    @Autowired
    private ComprobanteRepository repository;

    @Autowired
    private ListaPrecioService listaService;

    @Autowired
    private VentaRepository ventaRepository;

    @Autowired
    private ListaPrecioRepository listaRepository;

    private UUID puntoVentaId;
    private UUID listaId;

    @BeforeEach
    void crearPuntoVenta() {
        puntoVentaId = service.crearPuntoVenta(
                ThreadLocalRandom.current().nextInt(1, 1_000_000), "Caja concurrente").getId();
        listaId = listaService.crear("NUM-" + UUID.randomUUID().toString().substring(0, 8), "Minorista").getId();
    }

    /**
     * Un comprobante referencia una venta por clave foránea, así que el test necesita
     * ventas reales. Alcanza con la cabecera: lo que se prueba acá es la numeración, no
     * el contenido de la venta.
     */
    private UUID nuevaVenta() {
        return ventaRepository.save(new Venta(listaRepository.findById(listaId).orElseThrow())).getId();
    }

    @Test
    void laNumeracionArrancaEnUnoYAvanzaDeAUno() {
        service.emitir(puntoVentaId, TipoComprobante.FACTURA, nuevaVenta(), BigDecimal.TEN, null);
        service.emitir(puntoVentaId, TipoComprobante.FACTURA, nuevaVenta(), BigDecimal.TEN, null);

        List<Comprobante> emitidos =
                repository.findByPuntoVentaIdAndTipoOrderByNumeroAsc(puntoVentaId, TipoComprobante.FACTURA);

        assertThat(emitidos).extracting(Comprobante::getNumero).containsExactly(1L, 2L);
    }

    @Test
    void cadaTipoDeComprobanteLlevaSuPropiaNumeracion() {
        service.emitir(puntoVentaId, TipoComprobante.FACTURA, nuevaVenta(), BigDecimal.TEN, null);
        Comprobante remito =
                service.emitir(puntoVentaId, TipoComprobante.REMITO, nuevaVenta(), BigDecimal.TEN, null);

        // El remito no hereda el número de la factura: son secuencias distintas.
        assertThat(remito.getNumero()).isEqualTo(1L);
    }

    @Test
    void dieciseisEmisionesConcurrentesNoDejanHuecosNiRepetidos() throws Exception {
        CountDownLatch largada = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(HILOS)) {
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < HILOS; i++) {
                futures.add(pool.submit(() -> {
                    largada.await();
                    return service.emitir(puntoVentaId, TipoComprobante.FACTURA,
                            nuevaVenta(), new BigDecimal("100.00"), null);
                }));
            }
            largada.countDown();
            for (Future<?> f : futures) {
                f.get();
            }
        }

        List<Long> numeros =
                repository.findByPuntoVentaIdAndTipoOrderByNumeroAsc(puntoVentaId, TipoComprobante.FACTURA)
                        .stream().map(Comprobante::getNumero).toList();

        // La aserción fuerte: la secuencia es exactamente 1..16. Sin huecos (ningún
        // número se perdió) y sin repetidos (ningún número se entregó dos veces).
        assertThat(numeros).containsExactlyElementsOf(
                java.util.stream.LongStream.rangeClosed(1, HILOS).boxed().toList());
    }

    @Test
    void elNumeroFormateadoSigueElEstiloPuntoDeVentaGuionCorrelativo() {
        Comprobante c = service.emitir(puntoVentaId, TipoComprobante.FACTURA,
                nuevaVenta(), BigDecimal.TEN, null);

        assertThat(c.getNumeroFormateado()).matches("\\d{4,}-00000001");
    }
}
