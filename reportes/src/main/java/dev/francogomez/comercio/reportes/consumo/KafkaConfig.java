package dev.francogomez.comercio.reportes.consumo;

import dev.francogomez.comercio.contratos.Topicos;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import org.apache.kafka.common.TopicPartition;

@Configuration
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    private static final long INTERVALO_REINTENTO_MS = 1000;
    private static final long REINTENTOS = 3;

    /**
     * Reintenta unas pocas veces y, si el evento sigue fallando, lo manda al dead letter
     * topic. Sin esto, el comportamiento por defecto de Spring Kafka es reintentar sobre
     * el mismo registro: un solo evento defectuoso frena la partición entera y todos los
     * que venían detrás quedan sin proyectar.
     */
    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, String> kafkaTemplate,
                                            MeterRegistry registry) {
        Counter alDlt = Counter.builder("comercio.reportes.eventos.dlt")
                .description("Eventos enviados al dead letter topic")
                .register(registry);

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (registro, excepcion) -> {
                    alDlt.increment();
                    log.error("Evento enviado al DLT tras agotar reintentos. offset={} causa={}",
                            registro.offset(), excepcion.getMessage());
                    // Se conserva la partición de origen para no perder el orden relativo
                    // de los eventos de una misma venta dentro del DLT.
                    return new TopicPartition(Topicos.VENTAS_DLT, registro.partition());
                });

        return new DefaultErrorHandler(recoverer, new FixedBackOff(INTERVALO_REINTENTO_MS, REINTENTOS));
    }
}
