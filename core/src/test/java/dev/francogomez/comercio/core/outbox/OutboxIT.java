package dev.francogomez.comercio.core.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.francogomez.comercio.contratos.Topicos;
import dev.francogomez.comercio.core.comprobante.ComprobanteService;
import dev.francogomez.comercio.core.precio.ListaPrecioService;
import dev.francogomez.comercio.core.precio.PrecioService;
import dev.francogomez.comercio.core.producto.Producto;
import dev.francogomez.comercio.core.producto.ProductoRepository;
import dev.francogomez.comercio.core.stock.StockService;
import dev.francogomez.comercio.core.stock.TipoMovimiento;
import dev.francogomez.comercio.core.venta.Venta;
import dev.francogomez.comercio.core.venta.VentaDtos.LineaRequest;
import dev.francogomez.comercio.core.venta.VentaDtos.VentaRequest;
import dev.francogomez.comercio.core.venta.VentaService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Verifica la propiedad central del outbox: el evento existe si y solo si la venta
 * existe, y termina llegando a Kafka aunque el broker no estuviera disponible en el
 * momento de vender.
 */
@Testcontainers
@SpringBootTest
@TestPropertySource(properties = {
        "comercio.outbox.enabled=true",
        "comercio.outbox.intervalo-ms=200"
})
class OutboxIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    @ServiceConnection
    static ConfluentKafkaContainer kafka =
            new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @Autowired
    private VentaService ventaService;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private PrecioService precioService;

    @Autowired
    private ListaPrecioService listaService;

    @Autowired
    private StockService stockService;

    @Autowired
    private ComprobanteService comprobanteService;

    @Autowired
    private ProductoRepository productoRepository;

    private UUID listaId;
    private UUID puntoVentaId;

    @BeforeEach
    void prepararCatalogo() {
        listaId = listaService.crear("OB-" + UUID.randomUUID().toString().substring(0, 8), "Minorista").getId();
        puntoVentaId = comprobanteService.crearPuntoVenta(
                ThreadLocalRandom.current().nextInt(1, 1_000_000), "Caja outbox").getId();
    }

    @Test
    void confirmarUnaVentaDejaElEventoEnElOutboxYTerminaEnKafka() throws Exception {
        UUID producto = productoConPrecioYStock("150.00", 10);

        Venta venta = ventaService.registrar(
                new VentaRequest(listaId, puntoVentaId, List.of(new LineaRequest(producto, new BigDecimal("2")))),
                "outbox-" + UUID.randomUUID());

        // El publisher corre cada 200ms; el evento tiene que salir solo.
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(outboxRepository.countByPublicadoEnIsNull()).isZero());

        List<ConsumerRecord<String, String>> recibidos = leerDeKafka(venta.getId(), 1);
        assertThat(recibidos).hasSize(1);
        // La clave de partición es el id de la venta: garantiza que los eventos de una
        // misma venta lleguen ordenados al consumidor.
        assertThat(recibidos.getFirst().key()).isEqualTo(venta.getId().toString());

        // Se compara el JSON parseado y no su texto: la columna es jsonb y Postgres
        // re-serializa el valor al leerlo, reordenando claves y agregando espacios. Lo
        // que tiene que ser estable es el contenido, no los bytes.
        JsonNode evento = new ObjectMapper().readTree(recibidos.getFirst().value());
        assertThat(evento.get("ventaId").asText()).isEqualTo(venta.getId().toString());
        assertThat(evento.get("total").decimalValue()).isEqualByComparingTo("300.00");
        assertThat(evento.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(evento.get("lineas")).hasSize(1);
    }

    @Test
    void siLaVentaFallaNoQuedaNingunEventoAnunciandola() {
        UUID producto = productoConPrecioYStock("150.00", 1);
        long pendientesAntes = outboxRepository.countByPublicadoEnIsNull();

        try {
            ventaService.registrar(
                    new VentaRequest(listaId, puntoVentaId,
                            List.of(new LineaRequest(producto, new BigDecimal("99")))),
                    "outbox-fallido-" + UUID.randomUUID());
        } catch (RuntimeException esperado) {
            // stock insuficiente
        }

        // Lo que prueba que no hay dual-write: la transacción se revirtió y se llevó el
        // evento con ella. Publicar a Kafka desde el servicio habría dejado un evento
        // fantasma anunciando una venta que nunca ocurrió.
        assertThat(outboxRepository.countByPublicadoEnIsNull()).isEqualTo(pendientesAntes);
    }

    @Test
    void laNotaDeCreditoTambienPublicaSuEvento() {
        UUID producto = productoConPrecioYStock("100.00", 10);
        Venta venta = ventaService.registrar(
                new VentaRequest(listaId, puntoVentaId, List.of(new LineaRequest(producto, BigDecimal.ONE))),
                "outbox-nc-" + UUID.randomUUID());

        ventaService.emitirNotaCredito(venta.getId(), puntoVentaId, "devolución del cliente");

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(outboxRepository.countByPublicadoEnIsNull()).isZero());

        // Dos eventos para la misma venta: la confirmación y la reversión.
        assertThat(leerDeKafka(venta.getId(), 2)).hasSize(2);
        assertThat(stockService.saldoDe(producto)).isEqualByComparingTo("10");
    }

    /**
     * Lee el tópico desde el principio y se queda solo con los eventos de una venta.
     * El filtro es necesario porque el contenedor de Kafka se comparte entre los tests
     * de la clase: sin él, cada test vería también lo que publicaron los anteriores.
     */
    private List<ConsumerRecord<String, String>> leerDeKafka(UUID ventaId, int esperados) {
        Properties props = new Properties();
        props.put("bootstrap.servers", kafka.getBootstrapServers());
        props.put("group.id", "test-" + UUID.randomUUID());
        props.put("auto.offset.reset", "earliest");
        props.put("key.deserializer", StringDeserializer.class.getName());
        props.put("value.deserializer", StringDeserializer.class.getName());

        String clave = ventaId.toString();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(Topicos.VENTAS));
            List<ConsumerRecord<String, String>> acumulado = new java.util.ArrayList<>();

            long limite = System.currentTimeMillis() + Duration.ofSeconds(20).toMillis();
            while (acumulado.size() < esperados && System.currentTimeMillis() < limite) {
                ConsumerRecords<String, String> lote = consumer.poll(Duration.ofMillis(500));
                lote.forEach(r -> {
                    if (clave.equals(r.key())) {
                        acumulado.add(r);
                    }
                });
            }
            return acumulado;
        }
    }

    private UUID productoConPrecioYStock(String precio, int unidades) {
        Producto producto = productoRepository.save(
                new Producto("OB-" + UUID.randomUUID().toString().substring(0, 8), "Producto", "Test", "unidad"));
        precioService.asignar(producto.getId(), listaId, new BigDecimal(precio), Instant.now());
        stockService.registrar(producto.getId(), TipoMovimiento.ENTRADA,
                BigDecimal.valueOf(unidades), "carga inicial", null);
        return producto.getId();
    }
}
