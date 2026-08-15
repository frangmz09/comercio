package dev.francogomez.comercio.core.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La traducción de los choques de concurrencia a 409. Lo que se prueba acá no es que la
 * base rechace —eso lo verifican los tests de integración— sino la decisión de qué
 * merece un 409 y qué sigue siendo un 500, que es la única parte con lógica propia.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/productos");

    private ResponseEntity<ApiError> integridadCon(String sqlState) {
        var causa = new SQLException("violación reportada por la base", sqlState);
        return handler.handleIntegridad(new DataIntegrityViolationException("al escribir", causa), request);
    }

    @Test
    @DisplayName("una clave duplicada es un conflicto con el estado, no un fallo del servidor")
    void unaClaveDuplicadaDevuelve409() {
        var respuesta = integridadCon("23505");

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().message()).contains("reintentá");
        assertThat(respuesta.getBody().path()).isEqualTo("/api/v1/productos");
    }

    @Test
    @DisplayName("el solapamiento de rangos y los CHECK también son 409")
    void laExclusionYElCheckDevuelven409() {
        assertThat(integridadCon("23P01").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(integridadCon("23514").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("un NOT NULL sigue siendo 500: es un defecto del código, no una carrera")
    void unaColumnaObligatoriaSinValorDevuelve500() {
        var respuesta = integridadCon("23502");

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().message()).isEqualTo("Error interno inesperado");
    }

    @Test
    @DisplayName("una violación sin SQLException detrás no se asume conflicto")
    void sinCausaSqlDevuelve500() {
        var respuesta = handler.handleIntegridad(
                new DataIntegrityViolationException("sin causa de base"), request);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("perder un bloqueo optimista o no poder tomar uno pesimista es reintentable")
    void losFallosDeBloqueoDevuelven409() {
        var optimista = handler.handleChoqueDeConcurrencia(
                new ObjectOptimisticLockingFailureException(Object.class, "id"), request);
        var pesimista = handler.handleChoqueDeConcurrencia(
                new CannotAcquireLockException("deadlock detected"), request);

        assertThat(optimista.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(pesimista.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
