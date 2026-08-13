package dev.francogomez.comercio.reportes.proyeccion;

import dev.francogomez.comercio.contratos.NotaCreditoEmitida;
import dev.francogomez.comercio.contratos.VentaConfirmada;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class ProyeccionService {

    private static final Logger log = LoggerFactory.getLogger(ProyeccionService.class);

    private final EventoProcesadoRepository eventos;
    private final VentaDiariaRepository ventasDiarias;
    private final ProductoVendidoRepository productos;
    private final Counter procesados;
    private final Counter duplicados;

    public ProyeccionService(EventoProcesadoRepository eventos,
                             VentaDiariaRepository ventasDiarias,
                             ProductoVendidoRepository productos,
                             MeterRegistry registry) {
        this.eventos = eventos;
        this.ventasDiarias = ventasDiarias;
        this.productos = productos;
        this.procesados = Counter.builder("comercio.reportes.eventos.procesados")
                .description("Eventos aplicados a las proyecciones")
                .register(registry);
        this.duplicados = Counter.builder("comercio.reportes.eventos.duplicados")
                .description("Eventos descartados por haber sido procesados antes")
                .register(registry);
    }

    /**
     * Aplica una venta confirmada. La marca de procesado y la actualización de las
     * proyecciones van en la misma transacción: si la proyección falla, la marca se
     * revierte con ella y el evento se vuelve a intentar. Marcar primero y proyectar
     * después dejaría el evento perdido ante cualquier error.
     */
    @Transactional
    public void aplicar(VentaConfirmada evento) {
        if (yaProcesado(evento.eventId(), VentaConfirmada.class.getSimpleName())) {
            return;
        }

        LocalDate fecha = evento.confirmadaEn().atZone(ZoneOffset.UTC).toLocalDate();
        ventasDiarias.findById(fecha)
                .orElseGet(() -> ventasDiarias.save(new VentaDiaria(fecha)))
                .sumarVenta(evento.total());

        for (VentaConfirmada.Linea linea : evento.lineas()) {
            BigDecimal importe = linea.cantidad()
                    .multiply(linea.precioUnitario())
                    .setScale(2, RoundingMode.HALF_UP);

            productos.findById(linea.sku())
                    .orElseGet(() -> productos.save(new ProductoVendido(linea.sku())))
                    .acumular(linea.cantidad(), importe, evento.confirmadaEn());
        }

        procesados.increment();
    }

    @Transactional
    public void aplicar(NotaCreditoEmitida evento) {
        if (yaProcesado(evento.eventId(), NotaCreditoEmitida.class.getSimpleName())) {
            return;
        }

        LocalDate fecha = evento.emitidaEn().atZone(ZoneOffset.UTC).toLocalDate();
        ventasDiarias.findById(fecha)
                .orElseGet(() -> ventasDiarias.save(new VentaDiaria(fecha)))
                .sumarAnulacion(evento.total());

        procesados.increment();
    }

    @Transactional(readOnly = true)
    public List<VentaDiaria> ventasEntre(LocalDate desde, LocalDate hasta) {
        return ventasDiarias.findByFechaBetweenOrderByFechaDesc(desde, hasta);
    }

    @Transactional(readOnly = true)
    public List<ProductoVendido> masVendidos() {
        return productos.findTop10ByOrderByUnidadesDesc();
    }

    /**
     * El descarte de duplicados se apoya en la PK de {@code evento_procesado}: el INSERT
     * devuelve 0 si la fila ya existía. Un {@code exists()} previo no alcanzaría, porque
     * dos consumidores del mismo grupo procesando en paralelo lo pasarían los dos.
     */
    private boolean yaProcesado(java.util.UUID eventId, String tipo) {
        boolean nuevo = eventos.marcarProcesado(eventId, tipo) == 1;
        if (!nuevo) {
            duplicados.increment();
            log.debug("Evento {} ({}) ya estaba procesado, se descarta", eventId, tipo);
        }
        return !nuevo;
    }
}
