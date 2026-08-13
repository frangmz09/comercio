package dev.francogomez.comercio.core.stock;

import dev.francogomez.comercio.core.producto.Producto;
import dev.francogomez.comercio.core.producto.ProductoRepository;
import dev.francogomez.comercio.core.shared.ConflictException;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El test que justifica el diseño del módulo. Sin el SELECT FOR UPDATE sobre el saldo,
 * varios hilos leen el mismo valor, todos concluyen que hay stock y todos descuentan:
 * se vende mercadería que no existe y el saldo termina negativo o inconsistente.
 */
@Testcontainers
@SpringBootTest
class StockConcurrenciaIT {

    private static final int HILOS = 12;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private StockService stockService;

    @Autowired
    private ProductoRepository productoRepository;

    @Test
    void doceSalidasConcurrentesSobreDoceUnidadesDejanElSaldoEnCero() throws Exception {
        UUID productoId = crearProductoConStock(HILOS);

        Resultado resultado = ejecutarEnParalelo(HILOS, () ->
                stockService.registrar(productoId, TipoMovimiento.SALIDA, BigDecimal.ONE, "venta concurrente", null));

        assertThat(resultado.exitos()).isEqualTo(HILOS);
        assertThat(resultado.rechazos()).isZero();
        assertThat(stockService.saldoDe(productoId)).isEqualByComparingTo("0");
    }

    @Test
    void conStockParaLaMitadSoloLaMitadDeLosHilosVende() throws Exception {
        int disponible = HILOS / 2;
        UUID productoId = crearProductoConStock(disponible);

        Resultado resultado = ejecutarEnParalelo(HILOS, () ->
                stockService.registrar(productoId, TipoMovimiento.SALIDA, BigDecimal.ONE, "sobreventa", null));

        // Lo que se comprueba no es solo el saldo final, sino que nadie haya podido
        // descontar de más: exactamente `disponible` operaciones ganan y el resto falla.
        assertThat(resultado.exitos()).isEqualTo(disponible);
        assertThat(resultado.rechazos()).isEqualTo(HILOS - disponible);
        assertThat(stockService.saldoDe(productoId)).isEqualByComparingTo("0");
    }

    @Test
    void entradasConcurrentesSobreUnProductoSinSaldoPrevioNoPierdenNingunMovimiento() throws Exception {
        // Ejercita crearSiNoExiste: todos los hilos compiten por insertar la fila de
        // saldo al mismo tiempo y ninguno debe morir por clave duplicada.
        Producto producto = productoRepository.save(
                new Producto("CONC-" + UUID.randomUUID().toString().substring(0, 8), "Producto sin saldo", "Test", "unidad"));

        Resultado resultado = ejecutarEnParalelo(HILOS, () ->
                stockService.registrar(producto.getId(), TipoMovimiento.ENTRADA, BigDecimal.ONE, "carga", null));

        assertThat(resultado.exitos()).isEqualTo(HILOS);
        assertThat(stockService.saldoDe(producto.getId())).isEqualByComparingTo(String.valueOf(HILOS));
    }

    private UUID crearProductoConStock(int unidades) {
        Producto producto = productoRepository.save(
                new Producto("CONC-" + UUID.randomUUID().toString().substring(0, 8), "Producto de prueba", "Test", "unidad"));
        stockService.registrar(producto.getId(), TipoMovimiento.ENTRADA,
                BigDecimal.valueOf(unidades), "carga inicial", null);
        return producto.getId();
    }

    /**
     * Larga {@code hilos} tareas que quedan frenadas en una barrera y se sueltan todas
     * juntas, para que la competencia por el lock sea real y no una secuencia disfrazada.
     */
    private Resultado ejecutarEnParalelo(int hilos, Callable<?> tarea) throws Exception {
        CountDownLatch largada = new CountDownLatch(1);
        AtomicInteger exitos = new AtomicInteger();
        AtomicInteger rechazos = new AtomicInteger();

        try (ExecutorService pool = Executors.newFixedThreadPool(hilos)) {
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < hilos; i++) {
                futures.add(pool.submit(() -> {
                    largada.await();
                    try {
                        tarea.call();
                        exitos.incrementAndGet();
                    } catch (ConflictException e) {
                        rechazos.incrementAndGet();
                    }
                    return null;
                }));
            }
            largada.countDown();
            for (Future<?> f : futures) {
                f.get();
            }
        }
        return new Resultado(exitos.get(), rechazos.get());
    }

    private record Resultado(int exitos, int rechazos) {
    }
}
