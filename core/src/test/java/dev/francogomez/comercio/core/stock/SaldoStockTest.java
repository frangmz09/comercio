package dev.francogomez.comercio.core.stock;

import dev.francogomez.comercio.core.shared.ConflictException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reglas que no necesitan base: viven en la entidad y en el enum, así que se prueban
 * sin levantar contexto de Spring.
 */
class SaldoStockTest {

    @Test
    void unaEntradaSumaAlSaldo() {
        SaldoStock saldo = new SaldoStock(UUID.randomUUID());

        saldo.aplicar(new BigDecimal("10.5"));

        assertThat(saldo.getCantidad()).isEqualByComparingTo("10.5");
    }

    @Test
    void unaSalidaDentroDelSaldoDisponibleLoDescuenta() {
        SaldoStock saldo = new SaldoStock(UUID.randomUUID());
        saldo.aplicar(new BigDecimal("10"));

        saldo.aplicar(new BigDecimal("-4"));

        assertThat(saldo.getCantidad()).isEqualByComparingTo("6");
    }

    @Test
    void dejarElSaldoExactamenteEnCeroEstaPermitido() {
        SaldoStock saldo = new SaldoStock(UUID.randomUUID());
        saldo.aplicar(new BigDecimal("3"));

        saldo.aplicar(new BigDecimal("-3"));

        assertThat(saldo.getCantidad()).isEqualByComparingTo("0");
    }

    @Test
    void unaSalidaMayorAlSaldoSeRechazaYNoModificaElSaldo() {
        SaldoStock saldo = new SaldoStock(UUID.randomUUID());
        saldo.aplicar(new BigDecimal("2"));

        assertThatThrownBy(() -> saldo.aplicar(new BigDecimal("-5")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Stock insuficiente");

        assertThat(saldo.getCantidad()).isEqualByComparingTo("2");
    }

    @Test
    void elTipoDeMovimientoImponeElSignoSalvoEnElAjuste() {
        assertThat(TipoMovimiento.ENTRADA.aplicarSigno(new BigDecimal("-5"))).isEqualByComparingTo("5");
        assertThat(TipoMovimiento.SALIDA.aplicarSigno(new BigDecimal("5"))).isEqualByComparingTo("-5");
        assertThat(TipoMovimiento.AJUSTE.aplicarSigno(new BigDecimal("-2"))).isEqualByComparingTo("-2");
        assertThat(TipoMovimiento.AJUSTE.aplicarSigno(new BigDecimal("2"))).isEqualByComparingTo("2");
    }
}
