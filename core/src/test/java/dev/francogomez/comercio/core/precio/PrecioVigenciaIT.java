package dev.francogomez.comercio.core.precio;

import dev.francogomez.comercio.core.producto.Producto;
import dev.francogomez.comercio.core.producto.ProductoRepository;
import dev.francogomez.comercio.core.shared.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
class PrecioVigenciaIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private PrecioService precioService;

    @Autowired
    private ListaPrecioService listaService;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private PrecioRepository precioRepository;

    private UUID productoId;
    private UUID listaId;

    @BeforeEach
    void prepararCatalogo() {
        Producto producto = productoRepository.save(
                new Producto("PRE-" + UUID.randomUUID().toString().substring(0, 8), "Producto con precios", "Test", "unidad"));
        productoId = producto.getId();
        listaId = listaService.crear("MIN-" + UUID.randomUUID().toString().substring(0, 8), "Minorista").getId();
    }

    @Test
    void asignarUnPrecioNuevoCierraLaVigenciaDelAnterior() {
        // Truncados a microsegundos porque es la precisión que guarda timestamptz: la
        // entidad trunca al entrar, así que comparar contra un Instant con nanosegundos
        // fallaría por una diferencia que la base nunca podría representar.
        Instant ayer = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
        Instant ahora = Instant.now().truncatedTo(ChronoUnit.MICROS);

        Precio viejo = precioService.asignar(productoId, listaId, new BigDecimal("1000.00"), ayer);
        precioService.asignar(productoId, listaId, new BigDecimal("1200.00"), ahora);

        Precio viejoRecargado = precioRepository.findById(viejo.getId()).orElseThrow();
        assertThat(viejoRecargado.getVigenciaHasta()).isEqualTo(ahora);
        assertThat(viejoRecargado.estaAbierto()).isFalse();
    }

    @Test
    void elPrecioDeUnaFechaPasadaSigueSiendoElDeEseDia() {
        Instant hace10dias = Instant.now().minus(10, ChronoUnit.DAYS);
        Instant hace2dias = Instant.now().minus(2, ChronoUnit.DAYS);

        precioService.asignar(productoId, listaId, new BigDecimal("1000.00"), hace10dias);
        precioService.asignar(productoId, listaId, new BigDecimal("1500.00"), hace2dias);

        // Esta es la razón de ser de todo el módulo: reimprimir una venta vieja tiene
        // que devolver el precio que regía ese día, no el actual.
        assertThat(precioService.vigente(productoId, listaId, hace10dias.plus(1, ChronoUnit.DAYS)).getMonto())
                .isEqualByComparingTo("1000.00");
        assertThat(precioService.vigente(productoId, listaId, Instant.now()).getMonto())
                .isEqualByComparingTo("1500.00");
    }

    @Test
    void laBaseRechazaUnPrecioQueSeSolapaConUnoYaCerrado() {
        Instant hace10dias = Instant.now().minus(10, ChronoUnit.DAYS);
        Instant hace5dias = Instant.now().minus(5, ChronoUnit.DAYS);
        Instant hace7dias = Instant.now().minus(7, ChronoUnit.DAYS);

        precioService.asignar(productoId, listaId, new BigDecimal("1000.00"), hace10dias);
        precioService.asignar(productoId, listaId, new BigDecimal("1500.00"), hace5dias);

        // Retrodatar un precio al medio del historial: el servicio cierra el tramo que
        // encuentra en hace7dias, pero el precio nuevo nace abierto y por lo tanto pisa
        // al de hace5dias, que ya existía. El servicio no mira hacia adelante; la
        // constraint de exclusión sí, y ahí es donde se corta.
        // Cargar precios retroactivos sobre tramos ya cerrados no está soportado:
        // se rechaza de forma explícita en vez de dejar el historial inconsistente.
        assertThatThrownBy(() ->
                precioService.asignar(productoId, listaId, new BigDecimal("1100.00"), hace7dias))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("se solapa");
    }

    @Test
    void unProductoSinPrecioEnLaFechaConsultadaNoTieneVigente() {
        Instant ahora = Instant.now();
        precioService.asignar(productoId, listaId, new BigDecimal("1000.00"), ahora);

        assertThatThrownBy(() ->
                precioService.vigente(productoId, listaId, ahora.minus(1, ChronoUnit.DAYS)))
                .hasMessageContaining("no tiene precio vigente");
    }

    @Test
    void elHistorialDevuelveTodosLosPreciosDelMasNuevoAlMasViejo() {
        precioService.asignar(productoId, listaId, new BigDecimal("100.00"), Instant.now().minus(9, ChronoUnit.DAYS));
        precioService.asignar(productoId, listaId, new BigDecimal("200.00"), Instant.now().minus(6, ChronoUnit.DAYS));
        precioService.asignar(productoId, listaId, new BigDecimal("300.00"), Instant.now().minus(3, ChronoUnit.DAYS));

        assertThat(precioService.historial(productoId, listaId))
                .extracting(Precio::getMonto)
                .containsExactly(new BigDecimal("300.00"), new BigDecimal("200.00"), new BigDecimal("100.00"));
    }
}
