package dev.francogomez.comercio.core.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Punto de entrada para dejar un evento en el outbox. Se llama desde dentro de la
 * transacción de negocio: {@code MANDATORY} lo hace explícito, fallando en el arranque
 * de la operación si alguien intentara publicar fuera de una transacción, que es
 * justamente el error que el patrón viene a evitar.
 */
@Service
public class OutboxRegistrar {

    private final OutboxRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxRegistrar(OutboxRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public EventoOutbox registrar(UUID agregadoId, String tipo, Object evento) {
        try {
            String payload = objectMapper.writeValueAsString(evento);
            return repository.save(new EventoOutbox(agregadoId, tipo, payload));
        } catch (JsonProcessingException e) {
            // Serializar un evento propio no puede fallar por datos; si falla es un error
            // de programación y tiene que romper la operación, no perderse en un log.
            throw new IllegalStateException("No se pudo serializar el evento " + tipo, e);
        }
    }
}
