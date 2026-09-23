package dev.francogomez.comercio.core.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El camino feliz lo cubre {@link SeedDatosDemoIT} contra una base real. Acá quedan los
 * dos casos que en un Postgres limpio no ocurren: la base ya precargada y el choque con
 * datos cargados a mano.
 */
class SeedDatosDemoTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);
    private final ApplicationRunner seed = new SeedDatosDemo().crearDatosDeDemo(jdbc, tx);

    @Test
    @DisplayName("si la precarga ya está, cada reinicio la deja como está")
    void noVuelveACargarSiYaEsta() throws Exception {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any())).thenReturn(1);

        seed.run(null);

        verify(tx, never()).executeWithoutResult(any());
    }

    @Test
    @DisplayName("un choque con datos existentes no impide que la aplicación arranque")
    void unChoqueNoTumbaElArranque() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any())).thenReturn(0);
        doThrow(new DataIntegrityViolationException("uk_punto_venta_numero"))
                .when(tx).executeWithoutResult(any());

        assertThatCode(() -> seed.run(null)).doesNotThrowAnyException();
    }
}
