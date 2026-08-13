package dev.francogomez.comercio.reportes.consumo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.francogomez.comercio.contratos.NotaCreditoEmitida;
import dev.francogomez.comercio.contratos.VentaConfirmada;
import dev.francogomez.comercio.contratos.Topicos;
import dev.francogomez.comercio.reportes.proyeccion.ProyeccionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consume los eventos de ventas y los proyecta.
 *
 * <p>No hace falta desduplicar acá: {@link ProyeccionService} descarta los eventos ya
 * aplicados apoyándose en la clave primaria de {@code evento_procesado}. Esto importa
 * porque Kafka entrega at-least-once y el outbox reintenta: el mismo evento va a llegar
 * dos veces más de una vez en la vida del sistema.
 *
 * <p>Los eventos que fallan tras los reintentos configurados terminan en el dead letter
 * topic en lugar de bloquear la partición. Un evento con un payload que este consumidor
 * no entiende no puede frenar a todos los que vienen detrás.
 */
@Component
public class VentaListener {

    private static final Logger log = LoggerFactory.getLogger(VentaListener.class);

    private final ObjectMapper objectMapper;
    private final ProyeccionService proyeccion;

    public VentaListener(ObjectMapper objectMapper, ProyeccionService proyeccion) {
        this.objectMapper = objectMapper;
        this.proyeccion = proyeccion;
    }

    @KafkaListener(topics = Topicos.VENTAS, groupId = "${spring.kafka.consumer.group-id}")
    public void recibir(String payload) throws Exception {
        JsonNode json = objectMapper.readTree(payload);

        int version = json.path("schemaVersion").asInt();
        if (version > VentaConfirmada.CURRENT_SCHEMA_VERSION) {
            // Un productor más nuevo publicando un esquema que este consumidor no conoce.
            // Se falla explícitamente en lugar de proyectar datos a medias.
            throw new IllegalArgumentException(
                    "schemaVersion %d desconocida; este servicio soporta hasta %d"
                            .formatted(version, VentaConfirmada.CURRENT_SCHEMA_VERSION));
        }

        // La forma del evento distingue el tipo: la nota de crédito trae ventaOriginalId
        // y la confirmación trae lineas. Con dos tipos alcanza; si crecieran, convendría
        // un header con el nombre del evento.
        if (json.hasNonNull("ventaOriginalId")) {
            proyeccion.aplicar(objectMapper.treeToValue(json, NotaCreditoEmitida.class));
        } else if (json.hasNonNull("lineas")) {
            proyeccion.aplicar(objectMapper.treeToValue(json, VentaConfirmada.class));
        } else {
            throw new IllegalArgumentException("Evento no reconocido: " + payload);
        }

        log.debug("Evento proyectado: {}", json.path("eventId").asText());
    }
}
