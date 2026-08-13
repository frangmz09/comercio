package dev.francogomez.comercio.core.outbox;

import dev.francogomez.comercio.contratos.Topicos;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Vacía el outbox hacia Kafka. Corre aparte de la transacción de negocio: la venta ya
 * está confirmada cuando este publisher entra en acción, así que un Kafka caído demora
 * la proyección de reportes pero no impide vender.
 *
 * <p>La entrega es <em>at-least-once</em>: si el envío sale pero se cae antes de marcar
 * la fila, el evento se vuelve a publicar. Por eso el consumidor tiene que ser
 * idempotente, y lo es.
 */
@Component
@ConditionalOnProperty(name = "comercio.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int TAMANIO_LOTE = 100;
    private static final long TIMEOUT_ENVIO_SEGUNDOS = 10;

    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafka;
    private final Counter publicados;
    private final Counter fallidos;

    public OutboxPublisher(OutboxRepository repository,
                           KafkaTemplate<String, String> kafka,
                           MeterRegistry registry) {
        this.repository = repository;
        this.kafka = kafka;
        this.publicados = Counter.builder("comercio.outbox.publicados")
                .description("Eventos del outbox publicados con éxito")
                .register(registry);
        this.fallidos = Counter.builder("comercio.outbox.fallidos")
                .description("Intentos de publicación fallidos")
                .register(registry);

        // El gauge se lee de la base en cada scrape: es el indicador de que el outbox se
        // está vaciando. Si sube y no baja, Kafka está caído o el publisher no corre.
        registry.gauge("comercio.outbox.pendientes", repository, OutboxRepository::countByPublicadoEnIsNull);
    }

    @Scheduled(fixedDelayString = "${comercio.outbox.intervalo-ms:1000}")
    @Transactional
    public void publicarPendientes() {
        List<EventoOutbox> lote = repository.tomarPendientes(Limit.of(TAMANIO_LOTE));
        if (lote.isEmpty()) {
            return;
        }

        log.debug("Publicando {} eventos pendientes del outbox", lote.size());
        for (EventoOutbox evento : lote) {
            publicar(evento);
        }
    }

    private void publicar(EventoOutbox evento) {
        try {
            // Se espera la confirmación del broker: sin esto, marcar la fila como
            // publicada sería una promesa que Kafka todavía no hizo.
            kafka.send(Topicos.VENTAS, evento.getAgregadoId().toString(), evento.getPayload())
                    .get(TIMEOUT_ENVIO_SEGUNDOS, TimeUnit.SECONDS);
            evento.marcarPublicado();
            publicados.increment();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            evento.registrarFallo("Interrumpido al publicar");
            fallidos.increment();
        } catch (Exception e) {
            // No se relanza: un evento que falla no debe frenar al resto del lote ni
            // revertir los que ya se publicaron. Queda pendiente y se reintenta solo.
            evento.registrarFallo(e.getMessage());
            fallidos.increment();
            log.warn("No se pudo publicar el evento {} (intento {}): {}",
                    evento.getId(), evento.getIntentos(), e.getMessage());
        }
    }
}
