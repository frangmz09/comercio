package dev.francogomez.comercio.reportes.consumo;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.francogomez.comercio.contratos.NotaCreditoEmitida;
import dev.francogomez.comercio.contratos.Topicos;
import dev.francogomez.comercio.contratos.VentaConfirmada;
import dev.francogomez.comercio.reportes.proyeccion.ProyeccionService;
import dev.francogomez.comercio.reportes.proyeccion.VentaDiariaRepository;
import dev.francogomez.comercio.reportes.proyeccion.VentaDiaria;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Las dos propiedades que hacen confiable a un consumidor de eventos: que un evento
 * repetido no duplique el efecto, y que uno defectuoso no frene a los que vienen detrás.
 */
@Testcontainers
@SpringBootTest
class ConsumoEventosIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    @ServiceConnection
    static ConfluentKafkaContainer kafka =
            new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private VentaDiariaRepository ventasDiarias;

    @Autowired
    private ProyeccionService proyeccion;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void unaVentaConfirmadaSeProyectaEnElAgregadoDiario() throws Exception {
        Instant momento = Instant.now();
        LocalDate fecha = momento.atZone(ZoneOffset.UTC).toLocalDate();
        BigDecimal antes = totalDe(fecha);

        publicar(ventaDe(momento, "SKU-A", new BigDecimal("2"), new BigDecimal("100.00")));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(totalDe(fecha)).isEqualByComparingTo(antes.add(new BigDecimal("200.00"))));
    }

    @Test
    void elMismoEventoDosVecesSeProyectaUnaSolaVez() {
        Instant momento = Instant.now();
        LocalDate fecha = momento.atZone(ZoneOffset.UTC).toLocalDate();
        BigDecimal antes = totalDe(fecha);
        VentaConfirmada evento = ventaDe(momento, "SKU-B", BigDecimal.ONE, new BigDecimal("500.00"));

        // Se aplica directo sobre el servicio, sin pasar por Kafka: lo que se prueba es
        // la desduplicación, y así el test no depende de conseguir que el broker entregue
        // el mismo registro dos veces.
        proyeccion.aplicar(evento);
        proyeccion.aplicar(evento);

        assertThat(totalDe(fecha)).isEqualByComparingTo(antes.add(new BigDecimal("500.00")));
    }

    @Test
    void unaNotaDeCreditoAcumulaEnAnuladoSinBorrarLoVendido() {
        Instant momento = Instant.now();
        LocalDate fecha = momento.atZone(ZoneOffset.UTC).toLocalDate();

        proyeccion.aplicar(ventaDe(momento, "SKU-C", BigDecimal.ONE, new BigDecimal("300.00")));
        BigDecimal vendidoAntes = totalDe(fecha);

        proyeccion.aplicar(new NotaCreditoEmitida(
                1, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), momento, new BigDecimal("300.00")));

        VentaDiaria dia = ventasDiarias.findById(fecha).orElseThrow();
        // Lo vendido no se toca: se registra la anulación aparte para poder explicar por
        // qué bajó el neto.
        assertThat(dia.getTotalVendido()).isEqualByComparingTo(vendidoAntes);
        assertThat(dia.getTotalAnulado()).isGreaterThanOrEqualTo(new BigDecimal("300.00"));
        assertThat(dia.getNeto()).isEqualByComparingTo(vendidoAntes.subtract(dia.getTotalAnulado()));
    }

    @Test
    void unEventoIlegibleTerminaEnElDeadLetterTopicYNoFrenaAlSiguiente() throws Exception {
        Instant momento = Instant.now();
        LocalDate fecha = momento.atZone(ZoneOffset.UTC).toLocalDate();
        BigDecimal antes = totalDe(fecha);

        kafkaTemplate.send(Topicos.VENTAS, UUID.randomUUID().toString(),
                "{\"esto\":\"no es un evento conocido\"}").get();
        publicar(ventaDe(momento, "SKU-D", BigDecimal.ONE, new BigDecimal("77.00")));

        // La prueba de que la partición no quedó bloqueada: el evento válido publicado
        // después del defectuoso igual se proyectó.
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(totalDe(fecha)).isEqualByComparingTo(antes.add(new BigDecimal("77.00"))));

        assertThat(leerDlt()).isNotEmpty();
    }

    private BigDecimal totalDe(LocalDate fecha) {
        return ventasDiarias.findById(fecha).map(VentaDiaria::getTotalVendido).orElse(BigDecimal.ZERO);
    }

    private VentaConfirmada ventaDe(Instant momento, String sku, BigDecimal cantidad, BigDecimal precio) {
        return new VentaConfirmada(
                VentaConfirmada.CURRENT_SCHEMA_VERSION,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "0001-00000001",
                momento,
                List.of(new VentaConfirmada.Linea(sku, cantidad, precio)),
                cantidad.multiply(precio));
    }

    private void publicar(VentaConfirmada evento) throws Exception {
        kafkaTemplate.send(Topicos.VENTAS, evento.ventaId().toString(),
                objectMapper.writeValueAsString(evento)).get();
    }

    private List<ConsumerRecord<String, String>> leerDlt() {
        Properties props = new Properties();
        props.put("bootstrap.servers", kafka.getBootstrapServers());
        props.put("group.id", "dlt-" + UUID.randomUUID());
        props.put("auto.offset.reset", "earliest");
        props.put("key.deserializer", StringDeserializer.class.getName());
        props.put("value.deserializer", StringDeserializer.class.getName());

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(Topicos.VENTAS_DLT));
            List<ConsumerRecord<String, String>> acumulado = new java.util.ArrayList<>();

            long limite = System.currentTimeMillis() + Duration.ofSeconds(30).toMillis();
            while (acumulado.isEmpty() && System.currentTimeMillis() < limite) {
                ConsumerRecords<String, String> lote = consumer.poll(Duration.ofMillis(500));
                lote.forEach(acumulado::add);
            }
            return acumulado;
        }
    }
}
